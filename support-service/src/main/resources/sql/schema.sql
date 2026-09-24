-- ==========================================
-- 1. Notices 테이블
-- ==========================================
CREATE TABLE notices (
    notice_id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    author_id VARCHAR(50) NOT NULL,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    view_count BIGINT NOT NULL DEFAULT 0,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_notices_public_list
    ON notices(status, is_pinned, published_at);

CREATE INDEX idx_notices_category_status
    ON notices(category, status);

-- ==========================================
-- 2. FAQs 테이블
-- ==========================================
CREATE TABLE faqs (
    faq_id BIGSERIAL PRIMARY KEY,
    question VARCHAR(300) NOT NULL,
    answer TEXT NOT NULL,
    category VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    display_order INTEGER NOT NULL DEFAULT 0,
    author_id VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_faqs_public_list
    ON faqs(status, category, display_order);

CREATE INDEX idx_faqs_updated_at
    ON faqs(updated_at);

-- ==========================================
-- 3. 1:1 문의 테이블
-- ==========================================
CREATE TABLE inquiries (
    inquiry_id BIGSERIAL PRIMARY KEY,
    requester_id VARCHAR(50) NOT NULL,
    category VARCHAR(30) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_inquiries_requester_created
    ON inquiries(requester_id, created_at DESC);

CREATE INDEX idx_inquiries_status_created
    ON inquiries(status, created_at);

CREATE INDEX idx_inquiries_category_status
    ON inquiries(category, status);

-- ==========================================
-- 4. 1:1 문의 답변 테이블
-- ==========================================
CREATE TABLE inquiry_answers (
    inquiry_answer_id BIGSERIAL PRIMARY KEY,
    inquiry_id BIGINT NOT NULL UNIQUE,
    responder_id VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_inquiry_answers_inquiry
        FOREIGN KEY (inquiry_id) REFERENCES inquiries(inquiry_id)
);
