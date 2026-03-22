package com.example.orderpayment.dto;

public record PaymentSnapshot(
        String status,
        long authorizedAmount,
        long capturedAmount,
        long refundedAmount,
        long version) {}
