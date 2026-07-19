-- ==========================================
-- 1. Events 테이블
-- ==========================================
CREATE TABLE events (
    event_id BIGSERIAL PRIMARY KEY,
    artist_name VARCHAR(100) NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    venue VARCHAR(100) NOT NULL,
    venue_address VARCHAR(255),
    poster_url VARCHAR(500),
    event_date_time TIMESTAMP NOT NULL,
    sale_start_at TIMESTAMP,
    sale_end_at TIMESTAMP,
    cancel_deadline_at TIMESTAMP,
    running_minutes INTEGER,
    age_limit INTEGER,
    total_seats INTEGER NOT NULL,
    available_seats INTEGER,
    status VARCHAR(30) NOT NULL,
    max_tickets_per_person INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_event_artist_name ON events(artist_name);
CREATE INDEX idx_event_status_date_time ON events(status, event_date_time);

-- ==========================================
-- 2. Areas 테이블
-- ==========================================
CREATE TABLE areas (
    area_id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    area_name VARCHAR(80) NOT NULL,
    grade VARCHAR(10) NOT NULL,
    price INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_event_area_name UNIQUE (event_id, area_name)
);

CREATE INDEX idx_area_event_id ON areas(event_id);
CREATE INDEX idx_area_event_name ON areas(event_id, area_name);

-- ==========================================
-- 3. Event layouts 테이블
-- ==========================================
CREATE TABLE event_layouts (
    layout_id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL UNIQUE,
    original_file_name VARCHAR(255),
    svg_text TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_event_layout_event_id ON event_layouts(event_id);

-- ==========================================
-- 4. Seats 테이블
-- ==========================================
CREATE TABLE seats (
    seat_id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    area_id BIGINT,
    zone VARCHAR(50) NOT NULL,
    seat_row INTEGER NOT NULL,
    seat_col INTEGER NOT NULL,
    grade VARCHAR(10) NOT NULL,
    price INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    position_x DOUBLE PRECISION,
    position_y DOUBLE PRECISION,
    seat_width DOUBLE PRECISION,
    seat_height DOUBLE PRECISION,
    rotation DOUBLE PRECISION,
    layout_angle DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_event_seat_location UNIQUE (event_id, zone, seat_row, seat_col)
);

CREATE INDEX idx_seat_event_location ON seats(event_id, zone, seat_row, seat_col);
CREATE INDEX idx_seat_area_id ON seats(area_id);


-- ==========================================
-- 5. Reservations 테이블
-- ==========================================
CREATE TABLE reservations (
    reservation_id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(50) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    event_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    reserved_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_reservations_order_id UNIQUE (order_id)
);

CREATE INDEX idx_reservation_user_id ON reservations(user_id);
CREATE INDEX idx_reservation_event_id ON reservations(event_id);


-- ==========================================
-- 6. Reservation deliveries
-- ==========================================
CREATE TABLE reservation_deliveries (
    reservation_delivery_id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    recipient_name VARCHAR(30) NOT NULL,
    recipient_phone VARCHAR(20) NOT NULL,
    zip_code VARCHAR(10) NOT NULL,
    address VARCHAR(255) NOT NULL,
    detail_address VARCHAR(255),
    delivery_message VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    carrier VARCHAR(50),
    tracking_number VARCHAR(80),
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_reservation_deliveries_reservation_id UNIQUE (reservation_id)
);

CREATE INDEX idx_reservation_delivery_reservation_id ON reservation_deliveries(reservation_id);
CREATE INDEX idx_reservation_delivery_status ON reservation_deliveries(status);


-- ==========================================
-- 7. Coupons
-- ==========================================
CREATE TABLE coupons (
    coupon_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    discount_type VARCHAR(30) NOT NULL,
    discount_value INTEGER NOT NULL,
    max_discount_amount INTEGER,
    min_order_amount INTEGER,
    valid_from TIMESTAMP,
    valid_until TIMESTAMP,
    valid_days_after_issue INTEGER,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_coupons_code UNIQUE (code)
);

CREATE INDEX idx_coupon_status_valid_until ON coupons(status, valid_until);
CREATE INDEX idx_coupon_status_valid_days_after_issue ON coupons(status, valid_days_after_issue);


-- ==========================================
-- 8. User coupons
-- ==========================================
CREATE TABLE user_coupons (
    user_coupon_id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_user_coupons_user_coupon UNIQUE (user_id, coupon_id)
);

CREATE INDEX idx_user_coupon_user_status ON user_coupons(user_id, status);
CREATE INDEX idx_user_coupon_coupon_id ON user_coupons(coupon_id);


-- ==========================================
-- 9. Reservation discounts
-- ==========================================
CREATE TABLE reservation_discounts (
    reservation_discount_id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    user_coupon_id BIGINT,
    discount_type VARCHAR(30) NOT NULL,
    discount_name VARCHAR(100) NOT NULL,
    coupon_discount_type VARCHAR(30),
    discount_value INTEGER,
    discount_amount INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reservation_discount_reservation_id ON reservation_discounts(reservation_id);
CREATE INDEX idx_reservation_discount_user_coupon_id ON reservation_discounts(user_coupon_id);


-- ==========================================
-- 10. Payments
-- ==========================================
CREATE TABLE payments (
    payment_id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    payment_no VARCHAR(60) NOT NULL,
    method VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    amount INTEGER NOT NULL,
    idempotency_key VARCHAR(100),
    bank_name VARCHAR(50),
    account_number VARCHAR(50),
    depositor_name VARCHAR(50),
    requested_at TIMESTAMP NOT NULL,
    paid_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_payments_reservation_id UNIQUE (reservation_id),
    CONSTRAINT uk_payments_payment_no UNIQUE (payment_no),
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX idx_payment_reservation_id ON payments(reservation_id);
CREATE INDEX idx_payment_status_expires_at ON payments(status, expires_at);


-- ==========================================
-- 11. Tickets
-- ==========================================
CREATE TABLE tickets (
    ticket_id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    reservation_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    price INTEGER NOT NULL
);

CREATE INDEX idx_ticket_reservation_id ON tickets(reservation_id);
CREATE INDEX idx_ticket_user_id ON tickets(user_id);
CREATE INDEX idx_ticket_seat_id ON tickets(seat_id);
