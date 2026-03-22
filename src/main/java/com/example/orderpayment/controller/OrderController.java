package com.example.orderpayment.controller;

import com.example.orderpayment.common.AppConstants;
import com.example.orderpayment.dto.CreateOrderRequest;
import com.example.orderpayment.dto.OrderDetailsResponse;
import com.example.orderpayment.dto.OrderResponse;
import com.example.orderpayment.service.IdempotencyResult;
import com.example.orderpayment.service.IdempotencyService;
import com.example.orderpayment.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OrderController {
    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    public OrderController(OrderService orderService, IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody CreateOrderRequest request) {
        IdempotencyResult<OrderResponse> result = idempotencyService.executeWithIdempotency(
                AppConstants.IdempotencyScope.createOrder(request.restaurantId()),
                idempotencyKey,
                request,
                HttpStatus.CREATED.value(),
                OrderResponse.class,
                () -> orderService.createOrder(request));
        return ResponseEntity.status(result.statusCode()).body(result.body());
    }

    @GetMapping("/{orderId}")
    @ResponseStatus(HttpStatus.OK)
    public OrderDetailsResponse getOrder(@PathVariable String orderId) {
        return orderService.getOrderDetails(orderId);
    }
}
