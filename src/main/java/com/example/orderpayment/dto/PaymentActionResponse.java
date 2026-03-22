package com.example.orderpayment.dto;

public record PaymentActionResponse(
        String orderId,
        String paymentId,
        String paymentStatus,
        long authorizedAmount,
        long capturedAmount,
        long refundedAmount,
        long version) {}
