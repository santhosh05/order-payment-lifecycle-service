package com.example.orderpayment.common;

import com.example.orderpayment.enums.PaymentOperation;

public final class AppConstants {
    private AppConstants() {}

    public static final String IDEMPOTENCY_KEY_HEADER_REQUIRED_MESSAGE = "Idempotency-Key header is required";

    public static final class IdempotencyScope {
        private IdempotencyScope() {}

        public static String createOrder(String restaurantId) {
            return "order:create:" + restaurantId;
        }

        public static String orderPayment(String orderId, PaymentOperation operation) {
            return "order:" + orderId + ":payment:" + operation.value();
        }
    }
}
