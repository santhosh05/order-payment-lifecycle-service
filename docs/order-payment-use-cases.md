# Order Payment Lifecycle Service

## 1. Problem Statement

Build a backend service that manages the complete lifecycle of order payments for restaurant orders. The service must support:

- Create order
- Authorize payment
- Capture payment
- Refund payment
- Fetch order with full payment history
- Generate daily reconciliation report

## 2. Assumptions

- One order has one active payment.
- One payment can be captured successfully only once.
- One payment can be refunded partially or fully through multiple refund operations.
- All write APIs are idempotent.
- Payment gateway calls are synchronous for this version.
- Reconciliation is computed using the restaurant's timezone.

## 3. Core Entities

### Restaurant

- `restaurant_id`
- `name`
- `timezone`

`Restaurant` is the source of business timezone. In many systems this may live in a separate restaurant or merchant service, but the payment service still depends on it. At order-creation time, `restaurant_timezone` should be copied onto the order so historical reconciliation remains stable even if the restaurant profile changes later.

### Order

- `order_id`
- `restaurant_id`
- `currency`
- `order_amount`
- `status`
- `created_at`
- `updated_at`

`Order` is the business object being paid for. It links the restaurant context to the payment lifecycle.

### Payment

- `payment_id`
- `order_id`
- `authorized_amount`
- `captured_amount`
- `refunded_amount`
- `status`
- `version`
- `created_at`

`version` can be used as an optimistic lock counter for concurrent capture or refund requests. It is useful to keep in the data model

### Payment Event

- `event_id`
- `order_id`
- `payment_id`
- `event_type`
- `amount`
- `idempotency_key`
- `gateway_reference`
- `created_at`

Payment events are append-only and together form the payment history for an order.

## 4. Payment Lifecycle

```text
ORDER_CREATED
  -> AUTHORIZED
  -> CAPTURED
  -> PARTIALLY_REFUNDED
  -> FULLY_REFUNDED
```

Failure cases:

- Authorization can fail before capture.
- Capture is rejected if payment is not already authorized.
- Refund is rejected if refund amount exceeds the remaining captured amount.

### Payment States

- `AUTHORIZED`
- `CAPTURED`
- `PARTIALLY_REFUNDED`
- `FULLY_REFUNDED`
- `AUTHORIZATION_FAILED`

## 5. Main Use Cases

### UC-01 Create Order

Client creates an order with `restaurant_id`, amount, currency, and restaurant timezone.  
Service validates input, stores the order in `ORDER_CREATED`, and returns `order_id`.

### UC-02 Authorize Payment

Client sends an authorization request with `order_id`, amount, payment details, and `Idempotency-Key`.  
Service validates the order state, calls the payment gateway, stores the authorization result, and records an `AUTHORIZED` or `AUTHORIZATION_FAILED` event.

### UC-03 Capture Payment

Client sends a capture request with `order_id`, capture amount, and `Idempotency-Key`.  
Service allows capture only if payment is in `AUTHORIZED` state. It records a `CAPTURED` event and updates the payment to `CAPTURED`.

Double capture is prevented by validating payment state inside a transaction.

### UC-04 Refund Payment

Client sends a refund request with `order_id`, refund amount, reason, and `Idempotency-Key`.  
Service verifies that:

- payment is already captured
- refund amount is greater than zero
- total refunded amount does not exceed captured amount

On success, service records a `REFUNDED` event and moves the payment to:

- `PARTIALLY_REFUNDED` if some captured amount remains
- `FULLY_REFUNDED` if the full captured amount has been refunded

### UC-05 Fetch Order with Payment History

Client requests an order by `order_id`.  
Service returns:

- order details
- current payment status
- authorized, captured, and refunded amounts
- full payment event history in time order

### UC-06 Generate Daily Reconciliation Report

Client or scheduler requests reconciliation for a business date and restaurant.

Service:

- looks up the restaurant timezone
- converts the local business day into a UTC window
- aggregates authorized, captured, and refunded amounts for that window
- returns net collected amount

## 6. Business Rules

1. Capture is allowed only after successful authorization.
2. Double capture must be prevented.
3. Refund total cannot exceed captured amount.
4. All write APIs must support idempotency.
5. Repeated requests with the same idempotency key must return the same result.
6. Fetch order must include full payment history.
7. Reconciliation must use restaurant timezone, not server timezone.

## 7. API Surface

```text
POST /api/restaurants
GET  /api/restaurants/{restaurantId}
POST /api
POST /api/{orderId}/payments/authorize
POST /api/{orderId}/payments/capture
POST /api/{orderId}/payments/refund
GET  /api/{orderId}
GET  /api/reconciliation/daily?date=YYYY-MM-DD&restaurantId=...
GET  /actuator/health
```

All write endpoints require an `Idempotency-Key` header.

## 8. Reconciliation Logic

For a given restaurant and business date:

- `window_start` = local date at `00:00:00` in restaurant timezone, converted to UTC
- `window_end` = next local date at `00:00:00` in restaurant timezone, converted to UTC

Use the half-open interval `[window_start, window_end)`.

Suggested report fields:

- `orders_created`
- `authorized_amount`
- `captured_amount`
- `refunded_amount`
- `net_collected = captured_amount - refunded_amount`

## 9. Acceptance Criteria

- Order can be created successfully.
- Authorization is idempotent.
- Capture fails if authorization has not happened.
- Capture cannot happen twice for the same payment.
- Partial refund is supported.
- Full refund is supported.
- Refund requests above captured amount are rejected.
- Order fetch returns complete payment history.
- Reconciliation uses restaurant local business-day boundaries.
