package com.example.orderpayment;

import com.example.orderpayment.repository.IdempotencyRecordRepository;
import com.example.orderpayment.repository.OrderRepository;
import com.example.orderpayment.repository.PaymentEventRepository;
import com.example.orderpayment.repository.PaymentRepository;
import com.example.orderpayment.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderPaymentLifecycleIntegrationTest {
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        paymentEventRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        restaurantRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
    }

    @Test
    void shouldSupportCaptureAndPartialAndFullRefund() throws Exception {
        String orderId = createOrder("rest-1");

        mockMvc.perform(post("/api/{orderId}/payments/authorize", orderId)
                        .header("Idempotency-Key", "auth-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000,"paymentMethod":"CARD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("AUTHORIZED"));

        mockMvc.perform(post("/api/{orderId}/payments/capture", orderId)
                        .header("Idempotency-Key", "cap-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("CAPTURED"));

        mockMvc.perform(post("/api/{orderId}/payments/refund", orderId)
                        .header("Idempotency-Key", "ref-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":300,"reason":"item unavailable"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_REFUNDED"));

        mockMvc.perform(post("/api/{orderId}/payments/refund", orderId)
                        .header("Idempotency-Key", "ref-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":700,"reason":"remaining refund"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("FULLY_REFUNDED"))
                .andExpect(jsonPath("$.refundedAmount").value(1000));

        mockMvc.perform(get("/api/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FULLY_REFUNDED"))
                .andExpect(jsonPath("$.events.length()").value(4));
    }

    @Test
    void shouldReturnCachedResponseForSameIdempotencyKey() throws Exception {
        String orderId = createOrder("rest-2");

        MvcResult first = mockMvc.perform(post("/api/{orderId}/payments/authorize", orderId)
                        .header("Idempotency-Key", "dup-auth-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000,"paymentMethod":"CARD"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/{orderId}/payments/authorize", orderId)
                        .header("Idempotency-Key", "dup-auth-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000,"paymentMethod":"CARD"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());
        assertThat(firstBody.get("paymentId").asText()).isEqualTo(secondBody.get("paymentId").asText());
    }

    @Test
    void shouldPreventDoubleCapture() throws Exception {
        String orderId = createOrder("rest-3");
        authorize(orderId, "auth-2", 1000);

        capture(orderId, "cap-2", 750);

        mockMvc.perform(post("/api/{orderId}/payments/capture", orderId)
                        .header("Idempotency-Key", "cap-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":750}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Capture is allowed only after authorization"));
    }

    @Test
    void shouldGenerateDailyReconciliationUsingRestaurantTimezone() throws Exception {
        String restaurantId = "rest-4";
        String orderId = createOrder(restaurantId);
        authorize(orderId, "auth-3", 1000);
        capture(orderId, "cap-4", 900);
        mockMvc.perform(post("/api/{orderId}/payments/refund", orderId)
                        .header("Idempotency-Key", "ref-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":200,"reason":"partial refund"}
                                """))
                .andExpect(status().isOk());

        String date = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();

        mockMvc.perform(get("/api/reconciliation/daily")
                        .param("date", date)
                        .param("restaurantId", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizedAmount").value(1000))
                .andExpect(jsonPath("$.capturedAmount").value(900))
                .andExpect(jsonPath("$.refundedAmount").value(200))
                .andExpect(jsonPath("$.netCollected").value(700));
    }

    @Test
    void shouldApplyLocalDayBoundariesAcrossDstTransition() throws Exception {
        String restaurantId = "rest-dst";
        String timezone = "America/New_York";
        createRestaurant(restaurantId, timezone, "USD");

        LocalDate businessDate = LocalDate.of(2026, 3, 8);
        ZoneId zoneId = ZoneId.of(timezone);
        Instant windowStart = businessDate.atStartOfDay(zoneId).toInstant();
        Instant windowEnd = businessDate.plusDays(1).atStartOfDay(zoneId).toInstant();

        String orderBefore = UUID.randomUUID().toString();
        String orderInside = UUID.randomUUID().toString();
        String orderAtEnd = UUID.randomUUID().toString();

        insertOrder(orderBefore, restaurantId, timezone, "USD", 1000, windowStart.minusSeconds(1));
        insertOrder(orderInside, restaurantId, timezone, "USD", 1000, windowStart.plusSeconds(1));
        insertOrder(orderAtEnd, restaurantId, timezone, "USD", 1000, windowEnd);

        insertPaymentEvent(orderInside, "AUTHORIZED", 100, windowStart.minusSeconds(1));
        insertPaymentEvent(orderInside, "AUTHORIZED", 200, windowStart);
        insertPaymentEvent(orderInside, "CAPTURED", 150, windowEnd.minusSeconds(1));
        insertPaymentEvent(orderInside, "CAPTURED", 50, windowEnd);
        insertPaymentEvent(orderInside, "REFUNDED", 30, windowStart.plusSeconds(3600));
        insertPaymentEvent(orderInside, "AUTHORIZATION_FAILED", 0, windowStart.plusSeconds(120));
        insertPaymentEvent(orderInside, "CAPTURE_FAILED", 0, windowStart.plusSeconds(180));
        insertPaymentEvent(orderInside, "CAPTURE_FAILED", 0, windowEnd);

        mockMvc.perform(get("/api/reconciliation/daily")
                        .param("date", businessDate.toString())
                        .param("restaurantId", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantTimezone").value(timezone))
                .andExpect(jsonPath("$.ordersCreated").value(1))
                .andExpect(jsonPath("$.authorizedAmount").value(200))
                .andExpect(jsonPath("$.capturedAmount").value(150))
                .andExpect(jsonPath("$.refundedAmount").value(30))
                .andExpect(jsonPath("$.netCollected").value(120))
                .andExpect(jsonPath("$.failedAuthCount").value(1))
                .andExpect(jsonPath("$.failedCaptureCount").value(1));
    }

    private String createOrder(String restaurantId) throws Exception {
        createRestaurant(restaurantId);
        MvcResult result = mockMvc.perform(post("/api")
                        .header("Idempotency-Key", "order-" + restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "restaurantId":"%s",
                                  "orderAmount":1000
                                }
                                """.formatted(restaurantId)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText();
    }

    private void createRestaurant(String restaurantId) throws Exception {
        createRestaurant(restaurantId, "Asia/Kolkata", "INR");
    }

    private void createRestaurant(String restaurantId, String timezone, String defaultCurrency) throws Exception {
        mockMvc.perform(post("/api/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "restaurantId":"%s",
                                  "name":"Restaurant %s",
                                  "timezone":"%s",
                                  "defaultCurrency":"%s"
                                }
                                """.formatted(restaurantId, restaurantId, timezone, defaultCurrency)))
                .andExpect(status().isCreated());
    }

    private void insertOrder(
            String orderId,
            String restaurantId,
            String restaurantTimezone,
            String currency,
            long orderAmount,
            Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO orders (
                    order_id,
                    restaurant_id,
                    restaurant_timezone,
                    currency,
                    order_amount,
                    status,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                orderId,
                restaurantId,
                restaurantTimezone,
                currency,
                orderAmount,
                "ORDER_CREATED",
                createdAt,
                createdAt);
    }

    private void insertPaymentEvent(String orderId, String eventType, long amount, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO payment_events (
                    event_id,
                    order_id,
                    payment_id,
                    event_type,
                    amount,
                    idempotency_key,
                    gateway_reference,
                    event_metadata,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(),
                orderId,
                null,
                eventType,
                amount,
                null,
                null,
                null,
                createdAt);
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

    private void capture(String orderId, String key, long amount) throws Exception {
        mockMvc.perform(post("/api/{orderId}/payments/capture", orderId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":%d}
                                """.formatted(amount)))
                .andExpect(status().isOk());
    }
}
