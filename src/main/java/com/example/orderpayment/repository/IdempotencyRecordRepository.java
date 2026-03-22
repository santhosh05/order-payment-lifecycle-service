package com.example.orderpayment.repository;

import com.example.orderpayment.entity.IdempotencyRecordEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, String> {
    Optional<IdempotencyRecordEntity> findByScopeAndIdempotencyKey(String scope, String idempotencyKey);

    @Modifying
    @Query("DELETE FROM IdempotencyRecordEntity r WHERE r.expiresAt < :now")
    int deleteExpiredRecords(@Param("now") Instant now);
}
