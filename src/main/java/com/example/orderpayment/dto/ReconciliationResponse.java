package com.example.orderpayment.dto;

public record ReconciliationResponse(
        String date,
        String restaurantId,
        String restaurantTimezone,
        long ordersCreated,
        long authorizedAmount,
        long capturedAmount,
        long refundedAmount,
        long netCollected,
        long failedAuthCount,
        long failedCaptureCount) {}
