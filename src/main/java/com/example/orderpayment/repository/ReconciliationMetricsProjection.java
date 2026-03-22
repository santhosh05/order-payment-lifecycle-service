package com.example.orderpayment.repository;

public interface ReconciliationMetricsProjection {
    long getOrdersCreated();

    long getAuthorizedAmount();

    long getCapturedAmount();

    long getRefundedAmount();

    long getFailedAuthCount();

    long getFailedCaptureCount();
}
