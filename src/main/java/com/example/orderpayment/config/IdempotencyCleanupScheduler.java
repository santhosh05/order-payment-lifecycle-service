package com.example.orderpayment.config;

import com.example.orderpayment.repository.IdempotencyRecordRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class IdempotencyCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupScheduler.class);

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyCleanupScheduler(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    @Scheduled(fixedRate = 3600000) // every hour
    @Transactional
    public void purgeExpiredRecords() {
        int deleted = idempotencyRecordRepository.deleteExpiredRecords(Instant.now());
        if (deleted > 0) {
            log.info("Purged {} expired idempotency records", deleted);
        }
    }
}
