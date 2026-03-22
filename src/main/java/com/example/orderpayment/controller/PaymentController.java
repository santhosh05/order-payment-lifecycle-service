package com.example.orderpayment.controller;

import com.example.orderpayment.common.AppConstants;
import com.example.orderpayment.dto.AuthorizePaymentRequest;
import com.example.orderpayment.dto.CapturePaymentRequest;
import com.example.orderpayment.dto.PaymentActionResponse;
import com.example.orderpayment.dto.RefundPaymentRequest;
import com.example.orderpayment.enums.PaymentOperation;
import com.example.orderpayment.service.IdempotencyResult;
import com.example.orderpayment.service.IdempotencyService;
import com.example.orderpayment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/{orderId}/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    public PaymentController(PaymentService paymentService, IdempotencyService idempotencyService) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping("/authorize")
    public ResponseEntity<PaymentActionResponse> authorizePayment(
            @PathVariable String orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuthorizePaymentRequest request) {
        IdempotencyResult<PaymentActionResponse> result = idempotencyService.executeWithIdempotency(
                AppConstants.IdempotencyScope.orderPayment(orderId, PaymentOperation.AUTHORIZE),
                idempotencyKey,
                request,
                HttpStatus.OK.value(),
                PaymentActionResponse.class,
                () -> paymentService.authorizePayment(orderId, request, idempotencyKey));
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    @PostMapping("/capture")
    public ResponseEntity<PaymentActionResponse> capturePayment(
            @PathVariable String orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CapturePaymentRequest request) {
        IdempotencyResult<PaymentActionResponse> result = idempotencyService.executeWithIdempotency(
                AppConstants.IdempotencyScope.orderPayment(orderId, PaymentOperation.CAPTURE),
                idempotencyKey,
                request,
                HttpStatus.OK.value(),
                PaymentActionResponse.class,
                () -> paymentService.capturePayment(orderId, request, idempotencyKey));
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    @PostMapping("/refund")
    public ResponseEntity<PaymentActionResponse> refundPayment(
            @PathVariable String orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RefundPaymentRequest request) {
        IdempotencyResult<PaymentActionResponse> result = idempotencyService.executeWithIdempotency(
                AppConstants.IdempotencyScope.orderPayment(orderId, PaymentOperation.REFUND),
                idempotencyKey,
                request,
                HttpStatus.OK.value(),
                PaymentActionResponse.class,
                () -> paymentService.refundPayment(orderId, request, idempotencyKey));
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }
}
