-- ==========================================
-- 1. Events 테이블 (수정 완료 🌟)
-- ==========================================
CREATE TABLE events (
    event_id BIGSERIAL PRIMARY KEY,
    artist_name VARCHAR(100) NOT NULL,
    title VARCHAR(100) NOT NULL,
    description TEXT,
    venue VARCHAR(100) NOT NULL,
    event_date_time TIMESTAMP NOT NULL,
    total_seats INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    max_tickets_per_person INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_event_artist_name ON events(artist_name);
CREATE INDEX idx_event_status_date_time ON events(status, event_date_time);


-- ==========================================
-- 2. Seats 테이블
-- ==========================================
CREATE TABLE seats (
    seat_id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    grade VARCHAR(10) NOT NULL,
    price INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,              -- 🌟 length 30 반영
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- (event_id, seat_number) 복합 유니크 제약 조건 (@UniqueConstraint 반영)
    CONSTRAINT uk_event_seat UNIQUE (event_id, seat_number),
    -- 데이터 무결성을 위한 외래키 설정
    CONSTRAINT fk_seat_event FOREIGN KEY (event_id) REFERENCES events(event_id) ON DELETE CASCADE
);

CREATE INDEX idx_seat_event_id ON seats(event_id);


-- ==========================================
-- 3. Reservations (예매 마스터) 테이블
-- ==========================================
CREATE TABLE reservations (
    reservation_id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,            -- 🌟 BIGINT에서 String(VARCHAR)으로 변경 반영
    event_id BIGINT NOT NULL,                 -- 🌟 기존 구조에서 사방으로 흩어져있던 seat_id 제거 (Ticket으로 이동)
    status VARCHAR(30) NOT NULL,              -- 🌟 length 30 반영
    reserved_at TIMESTAMP NOT NULL,           -- 🌟 엔티티 설정 반영
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reservation_event FOREIGN KEY (event_id) REFERENCES events(event_id)
);

CREATE INDEX idx_reservation_user_id ON reservations(user_id);
CREATE INDEX idx_reservation_event_id ON reservations(event_id);


-- ==========================================
-- 4. Tickets (예매 상세 내역) 테이블 🌟 (신규 추가)
-- ==========================================
CREATE TABLE tickets (
    ticket_id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,            -- 🌟 엔티티 반영 (String)
    reservation_id BIGINT NOT NULL,           -- 🌟 N:1 관계 외래키
    event_id BIGINT NOT NULL,                 -- 🌟 N:1 관계 외래키
    seat_id BIGINT NOT NULL,                  -- 🌟 N:1 관계 외래키
    status VARCHAR(30) NOT NULL,              -- 🌟 length 30 반영

    CONSTRAINT fk_ticket_reservation FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_event FOREIGN KEY (event_id) REFERENCES events(event_id),
    CONSTRAINT fk_ticket_seat FOREIGN KEY (seat_id) REFERENCES seats(seat_id)
);

-- Ticket 조회를 연사 연동 최적화를 위한 인덱스들
CREATE INDEX idx_ticket_reservation_id ON tickets(reservation_id);
CREATE INDEX idx_ticket_user_id ON tickets(user_id);
CREATE INDEX idx_ticket_seat_id ON tickets(seat_id);