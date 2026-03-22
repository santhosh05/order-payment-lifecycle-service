package com.example.orderpayment.dto;

import java.time.Instant;

public record PaymentEventResponse(
        String eventId,
        String eventType,
        long amount,
        String gatewayReference,
        Instant createdAt) {}
