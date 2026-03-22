package com.example.orderpayment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "restaurants")
@Getter
@NoArgsConstructor
public class RestaurantEntity {
    @Id
    @Column(name = "restaurant_id", length = 36, nullable = false, updatable = false)
    private String restaurantId;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "timezone", length = 64, nullable = false)
    private String timezone;

    @Column(name = "default_currency", length = 3, nullable = false)
    private String defaultCurrency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static RestaurantEntity create(
            String restaurantId,
            String name,
            String timezone,
            String defaultCurrency) {
        RestaurantEntity restaurant = new RestaurantEntity();
        restaurant.restaurantId = restaurantId;
        restaurant.name = name;
        restaurant.timezone = timezone;
        restaurant.defaultCurrency = defaultCurrency;
        return restaurant;
    }

    @PrePersist
    private void initTimestamps() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}
