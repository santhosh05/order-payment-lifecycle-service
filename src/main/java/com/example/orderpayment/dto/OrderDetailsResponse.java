package com.example.orderpayment.dto;

import java.util.List;

public record OrderDetailsResponse(
        String orderId,
        String status,
        String restaurantId,
        String restaurantTimezone,
        String currency,
        long orderAmount,
        PaymentSnapshot payment,
        List<PaymentEventResponse> events) {}
