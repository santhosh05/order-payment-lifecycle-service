package com.example.orderpayment.repository;

import com.example.orderpayment.entity.PaymentEventEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, String> {
    List<PaymentEventEntity> findByOrderIdOrderByCreatedAtAsc(String orderId);

    @Query(
            value =
                    """
            SELECT
              (
                SELECT COUNT(*)
                FROM orders o2
                WHERE o2.restaurant_id = :restaurantId
                  AND o2.created_at >= :windowStart
                  AND o2.created_at < :windowEnd
              ) AS ordersCreated,
              COALESCE(SUM(CASE WHEN e.event_type = 'AUTHORIZED' THEN e.amount ELSE 0 END), 0) AS authorizedAmount,
              COALESCE(SUM(CASE WHEN e.event_type = 'CAPTURED' THEN e.amount ELSE 0 END), 0) AS capturedAmount,
              COALESCE(SUM(CASE WHEN e.event_type = 'REFUNDED' THEN e.amount ELSE 0 END), 0) AS refundedAmount,
              COALESCE(SUM(CASE WHEN e.event_type = 'AUTHORIZATION_FAILED' THEN 1 ELSE 0 END), 0) AS failedAuthCount,
              COALESCE(SUM(CASE WHEN e.event_type = 'CAPTURE_FAILED' THEN 1 ELSE 0 END), 0) AS failedCaptureCount
            FROM payment_events e
            JOIN orders o ON o.order_id = e.order_id
            WHERE o.restaurant_id = :restaurantId
              AND e.created_at >= :windowStart
              AND e.created_at < :windowEnd
            """,
            nativeQuery = true)
    ReconciliationMetricsProjection fetchDailyMetrics(
            @Param("restaurantId") String restaurantId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);
}
