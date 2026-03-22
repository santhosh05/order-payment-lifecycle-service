package com.example.orderpayment.dto;

public record OrderResponse(
        String orderId,
        String restaurantId,
        String restaurantTimezone,
        String currency,
        long orderAmount,
        String status) {}
