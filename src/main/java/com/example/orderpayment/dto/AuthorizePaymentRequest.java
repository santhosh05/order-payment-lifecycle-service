package com.example.orderpayment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AuthorizePaymentRequest(@Min(1) long amount, @NotBlank String paymentMethod) {}
