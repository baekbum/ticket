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
-- 2. Areas table
-- ==========================================
CREATE TABLE areas (
    area_id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    area_name VARCHAR(80) NOT NULL,
    grade VARCHAR(10) NOT NULL,
    price INTEGER NOT NULL,
    position_x DOUBLE PRECISION,
    position_y DOUBLE PRECISION,
    area_width DOUBLE PRECISION,
    area_height DOUBLE PRECISION,
    rotation DOUBLE PRECISION,
    layout_angle DOUBLE PRECISION,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_event_area_name UNIQUE (event_id, area_name)
);

CREATE INDEX idx_area_event_id ON areas(event_id);
CREATE INDEX idx_area_event_name ON areas(event_id, area_name);

-- ==========================================
-- 2. Seats 테이블
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
-- 3. Reservations 테이블
-- ==========================================
CREATE TABLE reservations (
    reservation_id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    event_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    reserved_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reservation_user_id ON reservations(user_id);
CREATE INDEX idx_reservation_event_id ON reservations(event_id);


-- ==========================================
-- 4. Tickets 테이블
-- ==========================================
CREATE TABLE tickets (
    ticket_id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    reservation_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL
);

CREATE INDEX idx_ticket_reservation_id ON tickets(reservation_id);
CREATE INDEX idx_ticket_user_id ON tickets(user_id);
CREATE INDEX idx_ticket_seat_id ON tickets(seat_id);
