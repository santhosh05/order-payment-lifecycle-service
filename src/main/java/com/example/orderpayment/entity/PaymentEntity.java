package com.example.orderpayment.entity;

import com.example.orderpayment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
public class PaymentEntity {
    @Id
    @Column(name = "payment_id", length = 36, nullable = false, updatable = false)
    private String paymentId;

    @Column(name = "order_id", length = 36, nullable = false, unique = true, updatable = false)
    private String orderId;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "authorized_amount", nullable = false)
    private long authorizedAmount;

    @Column(name = "captured_amount", nullable = false)
    private long capturedAmount;

    @Column(name = "refunded_amount", nullable = false)
    private long refundedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 40, nullable = false)
    private PaymentStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PaymentEntity createAuthorized(String orderId, String currency, long authorizedAmount) {
        PaymentEntity payment = new PaymentEntity();
        payment.paymentId = UUID.randomUUID().toString();
        payment.orderId = orderId;
        payment.currency = currency;
        payment.authorizedAmount = authorizedAmount;
        payment.capturedAmount = 0;
        payment.refundedAmount = 0;
        payment.status = PaymentStatus.AUTHORIZED;
        return payment;
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

    public void reAuthorize(long authorizedAmount) {
        this.authorizedAmount = authorizedAmount;
        this.capturedAmount = 0;
        this.refundedAmount = 0;
        this.status = PaymentStatus.AUTHORIZED;
    }

    public void capture(long capturedAmount) {
        this.capturedAmount = capturedAmount;
        this.status = PaymentStatus.CAPTURED;
    }

    public void addRefund(long refundAmount) {
        this.refundedAmount = this.refundedAmount + refundAmount;
        if (this.refundedAmount == this.capturedAmount) {
            this.status = PaymentStatus.FULLY_REFUNDED;
        } else {
            this.status = PaymentStatus.PARTIALLY_REFUNDED;
        }
    }

    public boolean isCaptured() {
        return this.capturedAmount > 0;
    }

    public long refundableAmount() {
        return this.capturedAmount - this.refundedAmount;
    }

    public boolean isRefundAllowed() {
        return this.status == PaymentStatus.CAPTURED || this.status == PaymentStatus.PARTIALLY_REFUNDED;
    }

    public void markAuthorizationFailed() {
        this.status = PaymentStatus.AUTHORIZATION_FAILED;
    }
}
