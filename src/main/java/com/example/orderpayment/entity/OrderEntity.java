package com.example.orderpayment.entity;

import com.example.orderpayment.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(name = "idx_orders_restaurant_created_at", columnList = "restaurant_id, created_at")
        })
@Getter
@NoArgsConstructor
public class OrderEntity {
    @Id
    @Column(name = "order_id", length = 36, nullable = false, updatable = false)
    private String orderId;

    @Column(name = "restaurant_id", length = 36, nullable = false)
    private String restaurantId;

    @Column(name = "restaurant_timezone", length = 64, nullable = false)
    private String restaurantTimezone;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "order_amount", nullable = false)
    private long orderAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 40, nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static OrderEntity create(
            String restaurantId,
            String restaurantTimezone,
            String currency,
            long orderAmount) {
        OrderEntity order = new OrderEntity();
        order.orderId = UUID.randomUUID().toString();
        order.restaurantId = restaurantId;
        order.restaurantTimezone = restaurantTimezone;
        order.currency = currency;
        order.orderAmount = orderAmount;
        order.status = OrderStatus.ORDER_CREATED;
        return order;
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

    public void updateStatus(OrderStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "Invalid order status transition: " + this.status + " -> " + newStatus);
        }
        this.status = newStatus;
    }
}
