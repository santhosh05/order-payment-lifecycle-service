package com.example.orderpayment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateOrderRequest(@NotBlank String restaurantId, @Min(1) long orderAmount) {}
