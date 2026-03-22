# Order Payment Lifecycle Service (Spring Boot)

Backend service to manage the full lifecycle of order payments:

- Register restaurant
- Create order
- Authorize payment (idempotent)
- Capture payment (double-capture prevention)
- Refund payment (partial and full)
- Fetch order with full payment history
- Generate daily reconciliation report (restaurant timezone aware)

## Tech Stack

- Java 21
- Spring Boot 3
- Spring Web + Spring Data JPA + Spring Actuator
- H2 (test profile) and MySQL 8 (runtime)
- Springdoc OpenAPI (Swagger UI)
- JUnit + MockMvc integration tests
- Testcontainers (MySQL concurrency tests)

## Project Structure

- `src/main/java/com/example/orderpayment`: application code
- `src/test/java/com/example/orderpayment`: integration tests
- `db/migrations/V1__init.sql`: baseline SQL schema script (reference)
- `docs/api/openapi.yaml`: static OpenAPI file
- `docs/api/postman_collection.json`: Postman collection

## Running Locally

### Prerequisites

- Java 21
- No local Gradle install required (use included wrapper)

If your machine default is Java 26, run Gradle with Java 21:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### Start the app

```bash
./gradlew bootRun
```

Service starts at `http://localhost:8080`.

Default runtime DB config is in `src/main/resources/application.properties`:

- URL: `jdbc:mysql://localhost:3306/order_payment`
- Username: `root`
- Password: `root`

### Run tests

```bash
./gradlew test
```

Tests run against H2 with Hibernate auto schema creation.
Runtime uses MySQL, so H2 tests do not fully validate MySQL-specific SQL dialect behavior, locking semantics, or constraint edge cases.

To run MySQL concurrency tests with Testcontainers (Docker required):

```bash
./gradlew test --tests '*OrderPaymentConcurrencyMySqlIntegrationTest'
```

If Docker is unavailable, these Testcontainers tests are auto-skipped.

### IntelliJ Sync Setup

If IntelliJ shows Gradle/JVM incompatibility:

1. Open `Settings > Build, Execution, Deployment > Build Tools > Gradle`
2. Set `Build and run using` to `Gradle`
3. Set `Run tests using` to `Gradle`
4. Set `Gradle distribution` to `Wrapper`
5. Set `Gradle JVM` to Java 21 (`corretto-21.0.10`)
6. Re-import the Gradle project

## Docker Setup

Run app + MySQL:

```bash
docker compose up --build
```

This starts:

- App: `http://localhost:8080`
- MySQL: `localhost:3306`

Schema is created automatically by Hibernate on startup.

## API Documentation

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Runtime OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Static OpenAPI file: `docs/api/openapi.yaml`
- Postman collection: `docs/api/postman_collection.json`

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/restaurants` | Create restaurant |
| GET | `/api/restaurants/{restaurantId}` | Get restaurant |
| POST | `/api` | Create order |
| GET | `/api/{orderId}` | Fetch order with payment history |
| POST | `/api/{orderId}/payments/authorize` | Authorize payment |
| POST | `/api/{orderId}/payments/capture` | Capture payment |
| POST | `/api/{orderId}/payments/refund` | Refund payment (partial/full) |
| GET | `/api/reconciliation/daily` | Daily reconciliation report |
| GET | `/actuator/health` | Health check |

All mutation endpoints require an `Idempotency-Key` header.
All monetary amounts are in the smallest currency unit (e.g. paise for INR, cents for USD).

## DB Schema

Core tables:

- `restaurants`
- `orders`
- `payments`
- `payment_events` (immutable event history)
- `idempotency_records` (auto-purged after 72h TTL)

Key indexes and constraints:

- `orders`: index `idx_orders_restaurant_created_at (restaurant_id, created_at)` for reconciliation window scans
- `payments`: unique constraint on `order_id` (one active payment per order)
- `payment_events`: index `idx_payment_events_order_created_at (order_id, created_at)` for order history reads
- `payment_events`: index `idx_payment_events_created_event_order (created_at, event_type, order_id)` for reconciliation aggregates
- `idempotency_records`: unique constraint `uq_idempotency_scope_key (scope, idempotency_key)` for idempotent write enforcement

Migrations:

- Baseline schema SQL is included at `db/migrations/V1__init.sql`.
- Hibernate manages schema updates at startup (`ddl-auto=update`).

## Key Design Decisions

1. **Order snapshots timezone and currency**
   - `restaurant_timezone` and `currency` are stored on `orders` to keep historical reconciliation stable even if restaurant defaults change.

2. **Event-ledger for payment history**
   - `payment_events` is append-only and provides an auditable timeline per order.

3. **Idempotency on order/payment write APIs**
   - Uses `(scope, idempotency_key)` uniqueness with per-resource scoping.
   - Stores SHA-256 request fingerprint and cached response.
   - Same key + same payload returns cached response.
   - Same key + different payload returns `422`.
   - Stale PROCESSING records (>5 min) are automatically cleaned up to handle crash recovery.
   - Expired records (>72h) are purged hourly by a scheduled cleanup job.

4. **Capture and refund guardrails**
   - Capture allowed only from `AUTHORIZED`.
   - Double capture blocked by state checks.
   - Refund total cannot exceed captured amount.

5. **Order state machine**
   - Order status transitions are validated: only valid transitions are allowed (e.g. `ORDER_CREATED -> PAYMENT_AUTHORIZED`, not `FULLY_REFUNDED -> PAYMENT_CAPTURED`).

6. **Concurrency control on payment writes**
   - Capture and refund read the payment row with a pessimistic write lock (`SELECT ... FOR UPDATE`) to serialize money-state transitions for the same order.
   - This keeps outcomes deterministic under contention (for example, losing concurrent refund fails business validation with `400` after winner commit, instead of a late `409` version conflict).
   - `payments.version` is still managed with JPA `@Version` as an additional safety net against stale writes.

7. **Timezone-correct reconciliation**
   - Daily reconciliation computes `[start, end)` boundaries using restaurant timezone before querying UTC timestamps.
   - Report timezone is resolved from the latest order snapshot (`orders.restaurant_timezone`) with fallback to restaurant profile.
   - Reconciliation metrics are fetched using a single aggregate query to reduce cross-query drift under concurrent writes.

## What's Not Implemented (Deliberate Scope)

- Async payment-gateway webhook flows and `PENDING_*` state recovery loops
- Multi-payment/split-payment and multi-capture flows for a single order
- Historical backfill logic if restaurant timezone is changed after orders already exist
- Flyway-based versioned migrations

## Example Flow

1. `POST /api/restaurants`
2. `POST /api` with `Idempotency-Key` and `restaurantId`
3. `POST /api/{orderId}/payments/authorize` with `Idempotency-Key`
4. `POST /api/{orderId}/payments/capture` with `Idempotency-Key`
5. `POST /api/{orderId}/payments/refund` with `Idempotency-Key`
6. `GET /api/{orderId}`
7. `GET /api/reconciliation/daily?date=YYYY-MM-DD&restaurantId=...`
