package com.example.orderpayment;

import com.example.orderpayment.repository.IdempotencyRecordRepository;
import com.example.orderpayment.repository.OrderRepository;
import com.example.orderpayment.repository.PaymentEventRepository;
import com.example.orderpayment.repository.PaymentRepository;
import com.example.orderpayment.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderPaymentErrorPathIntegrationTest {
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
        paymentEventRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        restaurantRepository.deleteAll();
        idempotencyRecordRepository.deleteAll();
    }

    @Test
    void shouldReturn404ForNonExistentOrder() throws Exception {
        mockMvc.perform(get("/api/{orderId}", "non-existent-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Order not found"));
    }

    @Test
    void shouldReturn404WhenAuthorizingNonExistentOrder() throws Exception {
        mockMvc.perform(post("/api/{orderId}/payments/authorize", "non-existent-id")
                        .header("Idempotency-Key", "auth-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000,"paymentMethod":"CARD"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Order not found"));
    }

    @Test
    void shouldRejectCaptureBeforeAuthorization() throws Exception {
        String orderId = createOrder("rest-err-1");

        mockMvc.perform(post("/api/{orderId}/payments/capture", orderId)
                        .header("Idempotency-Key", "cap-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Capture is allowed only after authorization"));
    }

    @Test
    void shouldRejectRefundBeforeCapture() throws Exception {
        String orderId = createOrder("rest-err-2");
        authorize(orderId, "auth-2", 1000);

        mockMvc.perform(post("/api/{orderId}/payments/refund", orderId)
                        .header("Idempotency-Key", "ref-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":500,"reason":"test"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Refund is allowed only after capture"));
    }

    @Test
    void shouldRejectRefundExceedingCapturedAmount() throws Exception {
        String orderId = createOrder("rest-err-3");
        authorize(orderId, "auth-3", 1000);
        capture(orderId, "cap-3", 1000);

        mockMvc.perform(post("/api/{orderId}/payments/refund", orderId)
                        .header("Idempotency-Key", "ref-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1500,"reason":"too much"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Refund amount exceeds captured amount"));
    }

    @Test
    void shouldRejectCaptureExceedingAuthorizedAmount() throws Exception {
        String orderId = createOrder("rest-err-4");
        authorize(orderId, "auth-4", 1000);

        mockMvc.perform(post("/api/{orderId}/payments/capture", orderId)
                        .header("Idempotency-Key", "cap-4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":2000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Capture amount exceeds authorized amount"));
    }

    @Test
    void shouldRejectMissingIdempotencyKeyHeader() throws Exception {
        mockMvc.perform(post("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"restaurantId":"rest-1","orderAmount":1000}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Idempotency-Key header is required"));
    }

    @Test
    void shouldRejectIdempotencyKeyReusedWithDifferentPayload() throws Exception {
        String orderId = createOrder("rest-err-5");
        authorize(orderId, "shared-key", 1000);

        // Same idempotency key, different payload (different amount)
        mockMvc.perform(post("/api/{orderId}/payments/authorize", orderId)
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":2000,"paymentMethod":"CARD"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Idempotency key reused with a different request payload"));
    }

    @Test
    void shouldRejectAuthorizationAmountMismatchingOrderAmount() throws Exception {
        String orderId = createOrder("rest-err-6");

        mockMvc.perform(post("/api/{orderId}/payments/authorize", orderId)
                        .header("Idempotency-Key", "auth-mismatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":500,"paymentMethod":"CARD"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Authorization amount must match order amount"));
    }

    @Test
    void shouldReturnOrderDetailsWithPaymentHistory() throws Exception {
        String orderId = createOrder("rest-err-7");
        authorize(orderId, "auth-det", 1000);
        capture(orderId, "cap-det", 1000);

        mockMvc.perform(get("/api/{orderId}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("PAYMENT_CAPTURED"))
                .andExpect(jsonPath("$.restaurantId").value("rest-err-7"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.orderAmount").value(1000))
                .andExpect(jsonPath("$.payment.status").value("CAPTURED"))
                .andExpect(jsonPath("$.payment.authorizedAmount").value(1000))
                .andExpect(jsonPath("$.payment.capturedAmount").value(1000))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].eventType").value("AUTHORIZED"))
                .andExpect(jsonPath("$.events[1].eventType").value("CAPTURED"));
    }

    @Test
    void shouldRejectRefundAfterFullRefund() throws Exception {
        String orderId = createOrder("rest-err-8");
        authorize(orderId, "auth-fr", 1000);
        capture(orderId, "cap-fr", 1000);

        mockMvc.perform(post("/api/{orderId}/payments/refund", orderId)
                        .header("Idempotency-Key", "ref-full")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":1000,"reason":"full refund"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("FULLY_REFUNDED"));

        mockMvc.perform(post("/api/{orderId}/payments/refund", orderId)
                        .header("Idempotency-Key", "ref-extra")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":100,"reason":"over refund"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Refund is allowed only after capture"));
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
        mockMvc.perform(post("/api/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "restaurantId":"%s",
                                  "name":"Restaurant %s",
                                  "timezone":"Asia/Kolkata",
                                  "defaultCurrency":"INR"
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
