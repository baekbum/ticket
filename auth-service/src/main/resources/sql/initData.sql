INSERT INTO users (user_id, password, role, name, phone_number, email, birth_date, address, is_blacklisted)
VALUES
-- 1. 관리자 (비밀번호: admin123! 를 BCrypt 암호화한 값)
('admin', '$2a$10$rLpkClIxwk.KWR6cIRepU.vbUN2slVC6uHoicUtEdA9CHM/lBeKMq', 'ROLE_ADMIN', '관리자', '010-0000-0000', 'admin@ticket.com', '1990-01-01', '서울시 강남구', false),

-- 2. 일반 유저 1 (비밀번호: user123! 를 BCrypt 암호화한 값)
('user01', '$2a$10$lrg6sFX60bKW98iXP5zW.emZYUDMvcptyeQr1bmWAxRmNHkVYS4MK', 'ROLE_USER', '김철수', '010-1111-2222', 'chulsoo@naver.com', '1995-05-10', '경기도 성남시', false),

-- 3. 일반 유저 2
('user02', '$2a$10$lrg6sFX60bKW98iXP5zW.emZYUDMvcptyeQr1bmWAxRmNHkVYS4MK', 'ROLE_USER', '이영희', '010-3333-4444', 'younghee@gmail.com', '1998-12-25', '서울시 송파구', false),

-- 4. 일반 유저 3
('user03', '$2a$10$lrg6sFX60bKW98iXP5zW.emZYUDMvcptyeQr1bmWAxRmNHkVYS4MK', 'ROLE_USER', '박민수', '010-5555-6666', 'minsu@kakao.com', '2000-03-15', '부산시 해운대구', false),

-- 5. 일반 유저 4 (블랙리스트 테스트용)
('baduser', '$2a$10$lrg6sFX60bKW98iXP5zW.emZYUDMvcptyeQr1bmWAxRmNHkVYS4MK', 'ROLE_USER', '불량유저', '010-9999-8888', 'bad@bad.com', '1992-07-07', '인천시 남동구', true);