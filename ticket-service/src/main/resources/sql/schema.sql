-- 1. Events 테이블
CREATE TABLE events (
    event_id BIGSERIAL PRIMARY KEY,
    artist_name VARCHAR(100) NOT NULL, -- 추가: 아티스트 이름 (예: 아이유)
    title VARCHAR(100) NOT NULL,
    description TEXT,
    venue VARCHAR(100) NOT NULL,
    event_date TIMESTAMP NOT NULL,
    total_seats INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_event_artist_name ON events(artist_name);

-- 2. Seats 테이블 (FK 제거, 인덱스 유지)
CREATE TABLE seats (
    seat_id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    grade VARCHAR(10) NOT NULL,
    -- 기존 NUMERIC(10, 2)에서 INTEGER로 변경
    price INTEGER NOT NULL,
    status VARCHAR(15) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- (event_id, seat_number) 복합 유니크 제약 조건
    CONSTRAINT uk_event_seat UNIQUE (event_id, seat_number)
);

-- 조회를 위한 인덱스
CREATE INDEX idx_seat_event_id ON seats(event_id);

-- 3. Reservations 테이블 (FK 제거, 인덱스 유지)
CREATE TABLE reservations (
    reservation_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    event_id BIGINT NOT NULL,
    reserved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(15) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_reservation_user_id ON reservations(user_id);
CREATE INDEX idx_reservation_event_id ON reservations(event_id);
CREATE INDEX idx_reservation_seat_id ON reservations(seat_id);