package com.example.orderpayment.service;

import com.example.orderpayment.dto.AuthorizePaymentRequest;
import com.example.orderpayment.dto.CapturePaymentRequest;
import com.example.orderpayment.dto.PaymentActionResponse;
import com.example.orderpayment.dto.RefundPaymentRequest;
import com.example.orderpayment.entity.OrderEntity;
import com.example.orderpayment.entity.PaymentEntity;
import com.example.orderpayment.entity.PaymentEventEntity;
import com.example.orderpayment.enums.OrderStatus;
import com.example.orderpayment.enums.PaymentEventType;
import com.example.orderpayment.enums.PaymentStatus;
import com.example.orderpayment.exception.ApiException;
import com.example.orderpayment.repository.PaymentEventRepository;
import com.example.orderpayment.repository.PaymentRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderService orderService;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentEventRepository paymentEventRepository,
            OrderService orderService) {
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.orderService = orderService;
    }

    @Transactional
    public PaymentActionResponse authorizePayment(
            String orderId, AuthorizePaymentRequest request, String idempotencyKey) {
        OrderEntity order = orderService.findOrderByIdOrThrow(orderId);
        validateOrderForAuthorization(order, request.amount());

        Optional<PaymentEntity> existingPayment = paymentRepository.findByOrderId(orderId);
        if (existingPayment.isPresent()
                && existingPayment.get().getStatus() != PaymentStatus.AUTHORIZATION_FAILED) {
            throw new ApiException(HttpStatus.CONFLICT, "Payment already exists for order");
        }

        PaymentEntity payment = existingPayment
                .map(existing -> {
                    existing.reAuthorize(request.amount());
                    return existing;
                })
                .orElseGet(() -> PaymentEntity.createAuthorized(orderId, order.getCurrency(), request.amount()));
        PaymentEntity savedPayment = paymentRepository.save(payment);

        orderService.updateOrderStatus(order, OrderStatus.PAYMENT_AUTHORIZED);
        createPaymentEvent(
                orderId,
                savedPayment.getPaymentId(),
                PaymentEventType.AUTHORIZED,
                request.amount(),
                idempotencyKey,
                UUID.randomUUID().toString(),
                null);

        return mapToPaymentActionResponse(orderId, savedPayment);
    }

    @Transactional
    public PaymentActionResponse capturePayment(
            String orderId, CapturePaymentRequest request, String idempotencyKey) {
        OrderEntity order = orderService.findOrderByIdOrThrow(orderId);
        PaymentEntity payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .filter(existingPayment -> existingPayment.getStatus() == PaymentStatus.AUTHORIZED)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Capture is allowed only after authorization"));

        if (payment.isCaptured()) {
            throw new ApiException(HttpStatus.CONFLICT, "Double capture is not allowed");
        }
        if (request.amount() > payment.getAuthorizedAmount()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Capture amount exceeds authorized amount");
        }

        payment.capture(request.amount());
        PaymentEntity savedPayment = paymentRepository.save(payment);

        orderService.updateOrderStatus(order, OrderStatus.PAYMENT_CAPTURED);
        createPaymentEvent(
                orderId,
                savedPayment.getPaymentId(),
                PaymentEventType.CAPTURED,
                request.amount(),
                idempotencyKey,
                UUID.randomUUID().toString(),
                null);

        return mapToPaymentActionResponse(orderId, savedPayment);
    }

    @Transactional
    public PaymentActionResponse refundPayment(
            String orderId, RefundPaymentRequest request, String idempotencyKey) {
        OrderEntity order = orderService.findOrderByIdOrThrow(orderId);
        PaymentEntity payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No payment found for order"));

        if (!payment.isRefundAllowed()) {
            throw new ApiException(HttpStatus.CONFLICT, "Refund is allowed only after capture");
        }

        if (request.amount() > payment.refundableAmount()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Refund amount exceeds captured amount");
        }

        payment.addRefund(request.amount());
        if (payment.getStatus() == PaymentStatus.FULLY_REFUNDED) {
            orderService.updateOrderStatus(order, OrderStatus.FULLY_REFUNDED);
        } else {
            orderService.updateOrderStatus(order, OrderStatus.PARTIALLY_REFUNDED);
        }
        PaymentEntity savedPayment = paymentRepository.save(payment);

        createPaymentEvent(
                orderId,
                savedPayment.getPaymentId(),
                PaymentEventType.REFUNDED,
                request.amount(),
                idempotencyKey,
                UUID.randomUUID().toString(),
                request.reason());

        return mapToPaymentActionResponse(orderId, savedPayment);
    }

    private void validateOrderForAuthorization(OrderEntity order, long amount) {
        if (order.getStatus() != OrderStatus.ORDER_CREATED) {
            throw new ApiException(HttpStatus.CONFLICT, "Order is not in a state eligible for authorization");
        }
        if (amount != order.getOrderAmount()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Authorization amount must match order amount");
        }
    }

    private void createPaymentEvent(
            String orderId,
            String paymentId,
            PaymentEventType eventType,
            long amount,
            String idempotencyKey,
            String gatewayReference,
            String metadata) {
        paymentEventRepository.save(PaymentEventEntity.create(
                orderId,
                paymentId,
                eventType,
                amount,
                idempotencyKey,
                gatewayReference,
                metadata));
    }

    private PaymentActionResponse mapToPaymentActionResponse(String orderId, PaymentEntity payment) {
        return new PaymentActionResponse(
                orderId,
                payment.getPaymentId(),
                payment.getStatus().name(),
                payment.getAuthorizedAmount(),
                payment.getCapturedAmount(),
                payment.getRefundedAmount(),
                payment.getVersion());
    }
}
