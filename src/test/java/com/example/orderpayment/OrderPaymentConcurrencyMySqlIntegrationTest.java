package com.example.orderpayment;

import com.example.orderpayment.repository.IdempotencyRecordRepository;
import com.example.orderpayment.repository.OrderRepository;
import com.example.orderpayment.repository.PaymentEventRepository;
import com.example.orderpayment.repository.PaymentRepository;
import com.example.orderpayment.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class OrderPaymentConcurrencyMySqlIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("order_payment")
            .withUsername("root")
            .withPassword("root");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @BeforeEach
    void cleanDb() {
        // Keep FK-safe deletion order: events -> payments -> orders -> restaurants.
        paymentEventRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        restaurantRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
    }

    @Test
    void concurrentCaptureShouldAllowOnlyOneSuccessfulCapture() throws Exception {
        String restaurantId = UUID.randomUUID().toString();
        String orderId = createOrder(restaurantId, 1000L);
        authorize(orderId, "auth-" + UUID.randomUUID(), 1000L);

        List<Integer> statuses = runConcurrently(
                () -> capture(orderId, "cap-a-" + UUID.randomUUID(), 1000L),
                () -> capture(orderId, "cap-b-" + UUID.randomUUID(), 1000L));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);

        mockMvc.perform(get("/api/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.capturedAmount").value(1000))
                .andExpect(jsonPath("$.payment.status").value("CAPTURED"));
    }

    @Test
    void concurrentRefundShouldPreventTotalExceedingCapturedAmount() throws Exception {
        String restaurantId = UUID.randomUUID().toString();
        String orderId = createOrder(restaurantId, 1000L);
        authorize(orderId, "auth-" + UUID.randomUUID(), 1000L);
        capture(orderId, "cap-" + UUID.randomUUID(), 1000L);

        List<Integer> statuses = runConcurrently(
                () -> refund(orderId, "ref-a-" + UUID.randomUUID(), 700L),
                () -> refund(orderId, "ref-b-" + UUID.randomUUID(), 500L));

        long successCount = statuses.stream().filter(status -> status == 200).count();
        int failureStatus = statuses.stream().filter(status -> status != 200).findFirst().orElseThrow();
        assertThat(successCount).isEqualTo(1);
        // With SELECT ... FOR UPDATE in refund flow, the losing request evaluates balance after winner commit.
        assertThat(failureStatus).isEqualTo(400);

        mockMvc.perform(get("/api/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.refundedAmount").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is(700),
                        org.hamcrest.Matchers.is(500))))
                .andExpect(jsonPath("$.payment.status").value("PARTIALLY_REFUNDED"));
    }

    @Test
    void concurrentCaptureWithSameIdempotencyKeyShouldKeepSingleCaptureAndReplaySuccess() throws Exception {
        String restaurantId = UUID.randomUUID().toString();
        String orderId = createOrder(restaurantId, 1000L);
        authorize(orderId, "auth-" + UUID.randomUUID(), 1000L);
        String sharedIdempotencyKey = "cap-same-" + UUID.randomUUID();

        List<Integer> statuses = runConcurrently(
                () -> capture(orderId, sharedIdempotencyKey, 1000L),
                () -> capture(orderId, sharedIdempotencyKey, 1000L));

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(capture(orderId, sharedIdempotencyKey, 1000L)).isEqualTo(200);

        mockMvc.perform(get("/api/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.capturedAmount").value(1000))
                .andExpect(jsonPath("$.payment.status").value("CAPTURED"));
    }

    private List<Integer> runConcurrently(ThrowingIntSupplier first, ThrowingIntSupplier second) throws Exception {
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> firstResult = executor.submit(() -> executeWithBarrier(first, readyLatch, startLatch));
            Future<Integer> secondResult = executor.submit(() -> executeWithBarrier(second, readyLatch, startLatch));

            boolean allReady = readyLatch.await(5, TimeUnit.SECONDS);
            assertThat(allReady).isTrue();
            startLatch.countDown();

            int firstStatus = firstResult.get(10, TimeUnit.SECONDS);
            int secondStatus = secondResult.get(10, TimeUnit.SECONDS);
            return List.of(firstStatus, secondStatus);
        } finally {
            executor.shutdownNow();
        }
    }

    private int executeWithBarrier(
            ThrowingIntSupplier supplier,
            CountDownLatch readyLatch,
            CountDownLatch startLatch) throws Exception {
        readyLatch.countDown();
        boolean started = startLatch.await(5, TimeUnit.SECONDS);
        assertThat(started).isTrue();
        return supplier.getAsInt();
    }

    private String createOrder(String restaurantId, long amount) throws Exception {
        createRestaurant(restaurantId);
        MvcResult result = mockMvc.perform(post("/api")
                        .header("Idempotency-Key", "order-" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "restaurantId":"%s",
                                  "orderAmount":%d
                                }
                                """.formatted(restaurantId, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("orderId")
                .asText();
    }

    private void createRestaurant(String restaurantId) throws Exception {
        mockMvc.perform(post("/api/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "restaurantId":"%s",
                                  "name":"Restaurant %s",
                                  "timezone":"America/Chicago",
                                  "defaultCurrency":"USD"
                                }
                                """.formatted(restaurantId, restaurantId)))
                .andExpect(status().isCreated());
    }

    private void authorize(String orderId, String key, long amount) throws Exception {
        mockMvc.perform(post("/api/{orderId}/payments/authorize", orderId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":%d,"paymentMethod":"CARD"}
                                """.formatted(amount)))
                .andExpect(status().isOk());
    }

    private int capture(String orderId, String key, long amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/{orderId}/payments/capture", orderId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":%d}
                                """.formatted(amount)))
                .andReturn();
        return result.getResponse().getStatus();
    }

    private int refund(String orderId, String key, long amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/{orderId}/payments/refund", orderId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":%d,"reason":"concurrency test"}
                                """.formatted(amount)))
                .andReturn();
        return result.getResponse().getStatus();
    }

    @FunctionalInterface
    private interface ThrowingIntSupplier {
        int getAsInt() throws Exception;
    }
}
