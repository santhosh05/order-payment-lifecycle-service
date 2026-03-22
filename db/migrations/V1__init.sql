CREATE TABLE IF NOT EXISTS restaurants (
    restaurant_id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    default_currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (restaurant_id)
);

CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(36) NOT NULL,
    restaurant_id VARCHAR(36) NOT NULL,
    restaurant_timezone VARCHAR(64) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    order_amount BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (order_id)
);

CREATE TABLE IF NOT EXISTS payments (
    payment_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    authorized_amount BIGINT NOT NULL,
    captured_amount BIGINT NOT NULL,
    refunded_amount BIGINT NOT NULL,
    status VARCHAR(40) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (payment_id),
    CONSTRAINT uk_payments_order_id UNIQUE (order_id)
);

CREATE TABLE IF NOT EXISTS payment_events (
    event_id VARCHAR(36) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    payment_id VARCHAR(36),
    event_type VARCHAR(40) NOT NULL,
    amount BIGINT NOT NULL,
    idempotency_key VARCHAR(128),
    gateway_reference VARCHAR(128),
    event_metadata TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (event_id)
);

CREATE TABLE IF NOT EXISTS idempotency_records (
    id VARCHAR(36) NOT NULL,
    scope VARCHAR(120) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_status INT,
    response_payload TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_scope_key UNIQUE (scope, idempotency_key)
);

CREATE INDEX idx_orders_restaurant_created_at
    ON orders (restaurant_id, created_at);

CREATE INDEX idx_payment_events_order_created_at
    ON payment_events (order_id, created_at);

CREATE INDEX idx_payment_events_created_event_order
    ON payment_events (created_at, event_type, order_id);
