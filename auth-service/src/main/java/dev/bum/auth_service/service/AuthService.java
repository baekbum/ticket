package dev.bum.auth_service.service;

import dev.bum.auth_service.exception.PasswordIncorrectException;
import dev.bum.auth_service.jpa.Auth;
import dev.bum.auth_service.jpa.AuthRepositoryImpl;
import dev.bum.auth_service.security.JwtTokenProvider;
import dev.bum.auth_service.vo.LoginInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepositoryImpl repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public Auth findByUserId(String userId) {
        return repository.findByUserId(userId);
    }

    public String LoginAndCreateToken(LoginInfo info) {
        log.info("login info : {}", info.toString());
        Auth auth = findByUserId(info.getUserId());

        log.info("id : {}", auth.getId());
        log.info("user id : {}", auth.getUserId());
        log.info("user password : {}", auth.getPassword());
        log.info("user role : {}", auth.getRole());

        // 비밀번호 검증
        if (!passwordEncoder.matches(info.getPassword(), auth.getPassword())) {
            throw new PasswordIncorrectException("사용자 정보가 일치하지 않습니다.");
        }

        return tokenProvider.createToken(auth.getUserId(), auth.getRole());
    }
}
