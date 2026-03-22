package com.example.orderpayment.repository;

import com.example.orderpayment.entity.PaymentEntity;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, String> {
    Optional<PaymentEntity> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT payment FROM PaymentEntity payment WHERE payment.orderId = :orderId")
    Optional<PaymentEntity> findByOrderIdForUpdate(@Param("orderId") String orderId);
}
