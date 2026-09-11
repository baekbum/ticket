INSERT INTO dummy_cards (
    user_id,
    card_company,
    card_number_hash,
    card_number_last4,
    cvc_hash,
    card_password_hash,
    customer_name,
    issued_at,
    expires_at,
    current_month_used_amount,
    limit_amount,
    active,
    created_at,
    updated_at
)
VALUES

-- =========================================================
-- 아이유 (iu)
-- 카드번호 : 1111-2222-3333-4444
-- CVC      : 123
-- 비밀번호 : qwer1234!
-- =========================================================
(
    'iu',
    'KB',
    'c6b25ffac5d2c50f41af3ac4d97d07d95fd4f2d5f9fcef98dc709e5741060508',
    '4444',
    '$2a$10$RVSN.aaI3L8MBu1o4RaKhOBehyTrb9./PMIN9c2owdNG6X4Vi7PbO',
    '$2a$10$Ynv1ur4butyP3x5wUE2zSujyRgz/daCeV2Pmr8fVHvyJKwnB8OmIa',
    '아이유',
    '2024-03-15',
    '2029-03-31',
    350000.00,
    5000000.00,
    true,
    NOW(),
    NOW()
),

-- =========================================================
-- 백범 (bum)
-- 카드번호 : 2222-3333-4444-5555
-- CVC      : 234
-- 비밀번호 : qwer1234!
-- =========================================================
(
    'bum',
    'SHINHAN',
    '03a9fde0ac9346605e3e3b671e93e48eea2ee316175c62572d8ca97a0dc05973',
    '5555',
    '$2a$10$9y4Gvy4gLX9HmCRENrnLleGGgpg4qt.GqFtw0AiUzO8M2bdhEtDzW',
    '$2a$10$Ynv1ur4butyP3x5wUE2zSujyRgz/daCeV2Pmr8fVHvyJKwnB8OmIa',
    '백범',
    '2025-01-10',
    '2030-01-31',
    1200000.00,
    10000000.00,
    true,
    NOW(),
    NOW()
),

-- =========================================================
-- 윤하 (younha)
-- 카드번호 : 3333-4444-5555-6666
-- CVC      : 345
-- 비밀번호 : qwer1234!
-- =========================================================
(
    'younha',
    'SAMSUNG',
    'c7e118b2b323a9dd6ca8d795b5551021d887363ef86958320297b243ee82aeaf',
    '6666',
    '$2a$10$6ihtWONY1KQ1t23rjbwPJ.fV0J1mgwrxPpfYW5BDMBrYvNpcasKQG',
    '$2a$10$Ynv1ur4butyP3x5wUE2zSujyRgz/daCeV2Pmr8fVHvyJKwnB8OmIa',
    '윤하',
    '2023-08-21',
    '2028-08-31',
    850000.00,
    7000000.00,
    true,
    NOW(),
    NOW()
),

-- =========================================================
-- 바미 (bami)
-- 카드번호 : 4444-5555-6666-7777
-- CVC      : 456
-- 비밀번호 : qwer1234!
-- =========================================================
(
    'bami',
    'HYUNDAI',
    '97c327e0aa0c40185ec3bda430dfe2b5dba8374bcf899d76bbd8c9394136ef00',
    '7777',
    '$2a$10$.scAQ6KjFaWV3jm2/VXKGOqexi3GAxONuDwoVMqc7CyIc1B8yYt6q',
    '$2a$10$Ynv1ur4butyP3x5wUE2zSujyRgz/daCeV2Pmr8fVHvyJKwnB8OmIa',
    '바미',
    '2024-11-05',
    '2029-11-30',
    100000.00,
    3000000.00,
    true,
    NOW(),
    NOW()
),

-- =========================================================
-- 김뭉릿 (kim)
-- 카드번호 : 5555-6666-7777-8888
-- CVC      : 567
-- 비밀번호 : qwer1234!
-- =========================================================
(
    'kim',
    'LOTTE',
    'f5f6cbbc4ae957adcec7ee851a0ba1d64314efb523be0e28a7bf96dfaee41a99',
    '8888',
    '$2a$10$KJSrfR1J0Ip2ZnzlUB.0De4nL0CQaHcAd4B/qBxCDVZoK608W2dFa',
    '$2a$10$Ynv1ur4butyP3x5wUE2zSujyRgz/daCeV2Pmr8fVHvyJKwnB8OmIa',
    '김뭉릿',
    '2025-06-12',
    '2030-06-30',
    2500000.00,
    15000000.00,
    true,
    NOW(),
    NOW()
);