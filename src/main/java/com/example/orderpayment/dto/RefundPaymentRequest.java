package com.example.orderpayment.dto;

import jakarta.validation.constraints.Min;

public record RefundPaymentRequest(@Min(1) long amount, String reason) {}
