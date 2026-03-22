package com.example.orderpayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRestaurantRequest(
        @NotBlank String restaurantId,
        @NotBlank String name,
        @NotBlank String timezone,
        @NotBlank @Size(min = 3, max = 3) String defaultCurrency) {}
