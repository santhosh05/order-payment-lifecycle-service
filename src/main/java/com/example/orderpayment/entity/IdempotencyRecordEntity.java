package com.example.orderpayment.entity;

import com.example.orderpayment.enums.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(
        name = "idempotency_records",
        uniqueConstraints = @UniqueConstraint(name = "uq_idempotency_scope_key", columnNames = {"scope", "idempotency_key"}))
@Getter
@NoArgsConstructor
public class IdempotencyRecordEntity {
    private static final long TTL_HOURS = 72;
    private static final Duration STALE_PROCESSING_THRESHOLD = Duration.ofMinutes(5);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(name = "scope", length = 120, nullable = false, updatable = false)
    private String scope;

    @Column(name = "idempotency_key", length = 128, nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", length = 64, nullable = false, updatable = false)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private IdempotencyStatus status;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    public static IdempotencyRecordEntity createProcessing(
            String scope,
            String idempotencyKey,
            String requestFingerprint) {
        IdempotencyRecordEntity record = new IdempotencyRecordEntity();
        record.scope = scope;
        record.idempotencyKey = idempotencyKey;
        record.requestFingerprint = requestFingerprint;
        record.status = IdempotencyStatus.PROCESSING;
        return record;
    }

    @PrePersist
    private void initTimestamps() {
        this.createdAt = Instant.now();
        this.expiresAt = this.createdAt.plus(TTL_HOURS, ChronoUnit.HOURS);
    }

    public void markCompleted(int responseStatus, String responsePayload) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseStatus = responseStatus;
        this.responsePayload = responsePayload;
    }

    public void markFailed(int responseStatus, String responsePayload) {
        this.status = IdempotencyStatus.FAILED;
        this.responseStatus = responseStatus;
        this.responsePayload = responsePayload;
    }

    public void markFailed() {
        this.status = IdempotencyStatus.FAILED;
    }

    public boolean isProcessing() {
        return IdempotencyStatus.PROCESSING.equals(this.status);
    }

    public boolean isStaleProcessing() {
        return isProcessing()
                && this.createdAt != null
                && Instant.now().isAfter(this.createdAt.plus(STALE_PROCESSING_THRESHOLD));
    }

    public boolean isCompleted() {
        return IdempotencyStatus.COMPLETED.equals(this.status);
    }

    public boolean hasFingerprintMismatch(String incomingFingerprint) {
        return !this.requestFingerprint.equals(incomingFingerprint);
    }
}
