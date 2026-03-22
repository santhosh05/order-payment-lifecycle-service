package com.example.orderpayment.service;

import com.example.orderpayment.common.AppConstants;
import com.example.orderpayment.entity.IdempotencyRecordEntity;
import com.example.orderpayment.exception.ApiException;
import com.example.orderpayment.repository.IdempotencyRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(
            IdempotencyRecordRepository idempotencyRecordRepository,
            ObjectMapper objectMapper) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.objectMapper = objectMapper;
    }

    public <T> IdempotencyResult<T> executeWithIdempotency(
            String scope,
            String idempotencyKey,
            Object requestPayload,
            int successStatus,
            Class<T> responseType,
            Supplier<T> operation) {
        validateIdempotencyKeyPresent(idempotencyKey);
        String requestFingerprint = computeRequestFingerprint(requestPayload);

        Optional<IdempotencyRecordEntity> existingRecord =
                idempotencyRecordRepository.findByScopeAndIdempotencyKey(scope, idempotencyKey);
        if (existingRecord.isPresent()) {
            return buildReplayResult(existingRecord.get(), requestFingerprint, responseType);
        }

        IdempotencyRecordEntity processingRecord =
                IdempotencyRecordEntity.createProcessing(scope, idempotencyKey, requestFingerprint);

        try {
            idempotencyRecordRepository.saveAndFlush(processingRecord);
        } catch (DataIntegrityViolationException exception) {
            IdempotencyRecordEntity concurrentRecord = idempotencyRecordRepository
                    .findByScopeAndIdempotencyKey(scope, idempotencyKey)
                    .orElseThrow(() -> exception);
            return buildReplayResult(concurrentRecord, requestFingerprint, responseType);
        }

        try {
            T responseBody = operation.get();
            String serializedResponse = serializeToJson(responseBody);
            processingRecord.markCompleted(successStatus, serializedResponse);
            idempotencyRecordRepository.save(processingRecord);
            return IdempotencyResult.fresh(successStatus, responseBody);
        } catch (ApiException apiException) {
            String serializedError = serializeToJson(apiException.getErrorBody());
            processingRecord.markFailed(apiException.getStatus().value(), serializedError);
            idempotencyRecordRepository.save(processingRecord);
            throw apiException;
        } catch (Exception exception) {
            processingRecord.markFailed();
            idempotencyRecordRepository.save(processingRecord);
            throw exception;
        }
    }

    private void validateIdempotencyKeyPresent(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, AppConstants.IDEMPOTENCY_KEY_HEADER_REQUIRED_MESSAGE);
        }
    }

    private <T> IdempotencyResult<T> buildReplayResult(
            IdempotencyRecordEntity record, String requestFingerprint, Class<T> responseType) {
        if (record.hasFingerprintMismatch(requestFingerprint)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "Idempotency key reused with a different request payload");
        }

        if (record.isProcessing()) {
            if (record.isStaleProcessing()) {
                idempotencyRecordRepository.delete(record);
                idempotencyRecordRepository.flush();
                throw new ApiException(HttpStatus.CONFLICT, "Previous request timed out. Please retry.");
            }
            throw new ApiException(HttpStatus.CONFLICT, "A request with this idempotency key is already in progress");
        }

        Integer cachedStatus = record.getResponseStatus();
        int responseStatus = cachedStatus == null ? HttpStatus.CONFLICT.value() : cachedStatus;
        if (record.getResponsePayload() == null || record.getResponsePayload().isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Cached response payload is missing");
        }

        if (responseStatus >= HttpStatus.BAD_REQUEST.value()) {
            throw buildCachedApiException(responseStatus, record.getResponsePayload());
        }

        T cachedBody = deserializeJson(record.getResponsePayload(), responseType);
        return IdempotencyResult.replay(responseStatus, cachedBody);
    }

    private String computeRequestFingerprint(Object requestPayload) {
        String payload = serializeToJson(requestPayload);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to compute request fingerprint");
        }
    }

    private String serializeToJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to serialize payload");
        }
    }

    private <T> T deserializeJson(String payload, Class<T> responseType) {
        try {
            return objectMapper.readValue(payload, responseType);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Cached response payload is corrupted");
        }
    }

    private ApiException buildCachedApiException(int statusCode, String payload) {
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return new ApiException(status, extractErrorMessage(payload));
    }

    private String extractErrorMessage(String payload) {
        try {
            Map<?, ?> responseBody = objectMapper.readValue(payload, Map.class);
            Object error = responseBody.get("error");
            if (error instanceof String errorMessage && !errorMessage.isBlank()) {
                return errorMessage;
            }
            return "Request failed";
        } catch (JsonProcessingException exception) {
            return "Request failed";
        }
    }
}
