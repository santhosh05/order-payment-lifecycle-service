package com.example.orderpayment.service;

public record IdempotencyResult<T>(
        boolean isReplay,
        int statusCode,
        T body) {
    public static <T> IdempotencyResult<T> fresh(int statusCode, T body) {
        return new IdempotencyResult<>(false, statusCode, body);
    }

    public static <T> IdempotencyResult<T> replay(int statusCode, T body) {
        return new IdempotencyResult<>(true, statusCode, body);
    }
}
