package com.example.orderpayment.service;

import com.example.orderpayment.dto.ReconciliationResponse;
import com.example.orderpayment.entity.RestaurantEntity;
import com.example.orderpayment.repository.PaymentEventRepository;
import com.example.orderpayment.repository.ReconciliationMetricsProjection;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {
    private final PaymentEventRepository paymentEventRepository;
    private final RestaurantService restaurantService;

    public ReconciliationService(
            PaymentEventRepository paymentEventRepository,
            RestaurantService restaurantService) {
        this.paymentEventRepository = paymentEventRepository;
        this.restaurantService = restaurantService;
    }

    @Transactional(readOnly = true)
    public ReconciliationResponse getDailyReconciliation(LocalDate date, String restaurantId) {
        RestaurantEntity restaurant = restaurantService.findRestaurantByIdOrThrow(restaurantId);
        ZoneId zoneId = ZoneId.of(restaurant.getTimezone());
        Instant windowStart = date.atStartOfDay(zoneId).toInstant();
        Instant windowEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant();

        ReconciliationMetricsProjection metrics = paymentEventRepository.fetchDailyMetrics(
                restaurantId, windowStart, windowEnd);

        return new ReconciliationResponse(
                date.toString(),
                restaurantId,
                restaurant.getTimezone(),
                metrics.getOrdersCreated(),
                metrics.getAuthorizedAmount(),
                metrics.getCapturedAmount(),
                metrics.getRefundedAmount(),
                metrics.getCapturedAmount() - metrics.getRefundedAmount(),
                metrics.getFailedAuthCount(),
                metrics.getFailedCaptureCount());
    }
}
