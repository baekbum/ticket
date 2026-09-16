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
