package com.example.orderpayment.enums;

import java.util.Set;

public enum OrderStatus {
    ORDER_CREATED,
    PAYMENT_AUTHORIZED,
    PAYMENT_CAPTURED,
    PARTIALLY_REFUNDED,
    FULLY_REFUNDED,
    PAYMENT_FAILED;

    public boolean canTransitionTo(OrderStatus target) {
        return allowedTransitions().contains(target);
    }

    private Set<OrderStatus> allowedTransitions() {
        return switch (this) {
            case ORDER_CREATED -> Set.of(PAYMENT_AUTHORIZED, PAYMENT_FAILED);
            case PAYMENT_AUTHORIZED -> Set.of(PAYMENT_CAPTURED, PAYMENT_FAILED);
            case PAYMENT_CAPTURED -> Set.of(PARTIALLY_REFUNDED, FULLY_REFUNDED);
            case PARTIALLY_REFUNDED -> Set.of(PARTIALLY_REFUNDED, FULLY_REFUNDED);
            case FULLY_REFUNDED, PAYMENT_FAILED -> Set.of();
        };
    }
}
