package com.example.orderpayment.dto;

import jakarta.validation.constraints.Min;

public record CapturePaymentRequest(@Min(1) long amount) {}
