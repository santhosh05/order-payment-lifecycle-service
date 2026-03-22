package com.example.orderpayment.service;

import com.example.orderpayment.dto.CreateOrderRequest;
import com.example.orderpayment.dto.OrderDetailsResponse;
import com.example.orderpayment.dto.OrderResponse;
import com.example.orderpayment.dto.PaymentEventResponse;
import com.example.orderpayment.dto.PaymentSnapshot;
import com.example.orderpayment.entity.OrderEntity;
import com.example.orderpayment.entity.RestaurantEntity;
import com.example.orderpayment.enums.OrderStatus;
import com.example.orderpayment.exception.ApiException;
import com.example.orderpayment.repository.OrderRepository;
import com.example.orderpayment.repository.PaymentEventRepository;
import com.example.orderpayment.repository.PaymentRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final RestaurantService restaurantService;

    public OrderService(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentEventRepository paymentEventRepository,
            RestaurantService restaurantService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.restaurantService = restaurantService;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        RestaurantEntity restaurant = restaurantService.findRestaurantByIdOrThrow(request.restaurantId());

        OrderEntity order = OrderEntity.create(
                request.restaurantId(),
                restaurant.getTimezone(),
                restaurant.getDefaultCurrency(),
                request.orderAmount());
        orderRepository.save(order);
        return mapToOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderDetailsResponse getOrderDetails(String orderId) {
        OrderEntity order = findOrderByIdOrThrow(orderId);
        PaymentSnapshot paymentSnapshot = paymentRepository.findByOrderId(orderId)
                .map(payment -> new PaymentSnapshot(
                        payment.getStatus().name(),
                        payment.getAuthorizedAmount(),
                        payment.getCapturedAmount(),
                        payment.getRefundedAmount(),
                        payment.getVersion()))
                .orElse(null);

        List<PaymentEventResponse> paymentEvents = paymentEventRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(event -> new PaymentEventResponse(
                        event.getEventId(),
                        event.getEventType().name(),
                        event.getAmount(),
                        event.getGatewayReference(),
                        event.getCreatedAt()))
                .toList();

        return new OrderDetailsResponse(
                order.getOrderId(),
                order.getStatus().name(),
                order.getRestaurantId(),
                order.getRestaurantTimezone(),
                order.getCurrency(),
                order.getOrderAmount(),
                paymentSnapshot,
                paymentEvents);
    }

    public OrderEntity findOrderByIdOrThrow(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
    }

    public void updateOrderStatus(OrderEntity order, OrderStatus status) {
        order.updateStatus(status);
        orderRepository.save(order);
    }

    private OrderResponse mapToOrderResponse(OrderEntity order) {
        return new OrderResponse(
                order.getOrderId(),
                order.getRestaurantId(),
                order.getRestaurantTimezone(),
                order.getCurrency(),
                order.getOrderAmount(),
                order.getStatus().name());
    }
}
