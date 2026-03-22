package com.example.orderpayment.exception;

import jakarta.persistence.OptimisticLockException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

    @Test
    void shouldMapSpringOptimisticLockingFailureToConflict() {
        ResponseEntity<Map<String, String>> response = globalExceptionHandler
                .handleOptimisticLockingFailure(new OptimisticLockingFailureException("stale write"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "Concurrent update detected. Please retry the request");
    }

    @Test
    void shouldMapJpaOptimisticLockExceptionToConflict() {
        ResponseEntity<Map<String, String>> response =
                globalExceptionHandler.handleOptimisticLockingFailure(new OptimisticLockException("stale row"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "Concurrent update detected. Please retry the request");
    }
}
