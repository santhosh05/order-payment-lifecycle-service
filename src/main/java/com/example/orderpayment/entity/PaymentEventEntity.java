package com.example.orderpayment.entity;

import com.example.orderpayment.enums.PaymentEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "payment_events",
        indexes = {
                @Index(name = "idx_payment_events_order_created_at", columnList = "order_id, created_at"),
                @Index(name = "idx_payment_events_created_event_order", columnList = "created_at, event_type, order_id")
        })
@Getter
@NoArgsConstructor
public class PaymentEventEntity {
    @Id
    @Column(name = "event_id", length = 36, nullable = false, updatable = false)
    private String eventId;

    @Column(name = "order_id", length = 36, nullable = false)
    private String orderId;

    @Column(name = "payment_id", length = 36)
    private String paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 40, nullable = false)
    private PaymentEventType eventType;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "gateway_reference", length = 128)
    private String gatewayReference;

    @Column(name = "event_metadata", columnDefinition = "TEXT")
    private String eventMetadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PaymentEventEntity create(
            String orderId,
            String paymentId,
            PaymentEventType eventType,
            long amount,
            String idempotencyKey,
            String gatewayReference,
            String metadata) {
        PaymentEventEntity event = new PaymentEventEntity();
        event.eventId = UUID.randomUUID().toString();
        event.orderId = orderId;
        event.paymentId = paymentId;
        event.eventType = eventType;
        event.amount = amount;
        event.idempotencyKey = idempotencyKey;
        event.gatewayReference = gatewayReference;
        event.eventMetadata = metadata;
        return event;
    }

    @PrePersist
    private void initCreatedAt() {
        this.createdAt = Instant.now();
    }
}
