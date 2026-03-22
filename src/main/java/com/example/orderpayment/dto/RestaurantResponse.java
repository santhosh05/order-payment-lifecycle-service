package com.example.orderpayment.dto;

public record RestaurantResponse(
        String restaurantId,
        String name,
        String timezone,
        String defaultCurrency) {}
