package dev.bum.auth_service.service;

import dev.bum.auth_service.audit.AuditLog;
import dev.bum.auth_service.audit.AuditContext;
import dev.bum.auth_service.exception.PasswordIncorrectException;
import dev.bum.auth_service.exception.RedisException;
import dev.bum.auth_service.exception.UserNotExistException;
import dev.bum.auth_service.jpa.Auth;
import dev.bum.auth_service.jpa.AuthRepository;
import dev.bum.common.jwt.dto.TokenResponse;
import dev.bum.common.service.auth.dto.LoginRequest;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.kafka.user.UserDtoForEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;

    @Transactional(readOnly = true)
    public Auth findByUserId(String userId) {
        return repository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public void validateInfo(LoginRequest info) {
        Auth auth = findByUserId(info.getUserId());

        comparePassword(info, auth);
    }

    /**
     * 토큰을 발급하는 메서드.
     * @param info
     * @return
     */
    @Transactional(readOnly = true)
    @AuditLog(action = "LOGIN", targetType = "AUTH")
    public TokenResponse LoginAndCreateToken(LoginRequest info) {
        log.info("login info : {}", info.toString());
        Auth auth = findByUserId(info.getUserId());
        AuditContext.setActor(auth);

        log.info("id : {}", auth.getId());
        log.info("user id : {}", auth.getUserId());
        log.info("user password : {}", auth.getPassword());
        log.info("user role : {}", auth.getRole());

        // 비밀번호 검증
        comparePassword(info, auth);

        TokenResponse tokens = tokenProvider.createToken(auth.getUserId(), auth.getRole().name());

        addRefreshTokenToRedis(auth.getUserId(), tokens.getRefreshToken());

        return tokens;
    }

    private void comparePassword(LoginRequest info, Auth auth) {
        if (!passwordEncoder.matches(info.getPassword(), auth.getPassword())) {
            throw new PasswordIncorrectException("사용자 정보가 일치하지 않습니다.");
        }
    }


    /**
     * refresh 토큰을 redis에 저장
     * @param userId
     * @param refreshToken
     */
    private void addRefreshTokenToRedis(String userId, String refreshToken) {
        try {
            redisTemplate.opsForValue().set(
                    "RT:" + userId,
                    refreshToken,
                    Duration.ofDays(14)
            );
        } catch (DataAccessException e) {
            throw new RedisException("Redis 오류 발생");
        }
    }

    /**
     * 카프카 토픽에서 데이터를 받아 유저를 추가하는 메서드
     * @param event
     */
    public void insertUserTopic(UserDtoForEvent event) {
        log.info("[유저 추가] : info : {}", event.toString());
        repository.insert(event);
    }

    /**
     * 카프카 토픽에서 데이터를 받아 유저 정보를 수정하는
     * @param event
     */
    public void updateUserTopic(UserDtoForEvent event) {
        log.info("[유저 수정] : info : {}", event.toString());
        repository.update(event);
    }

    /**
     * 카프카 토픽에서 데이터를 받아 유저를 삭제하는 메서드
     * @param event
     */
    public void deleteUserTopic(UserDtoForEvent event) {
        log.info("[유저 삭제] : info : {}", event.toString());
        repository.delete(event.getUserId());
    }

    /**
     * Refresh Token을 활용해 Access/Refresh Token 세트를 재발급(갱신)하는 메서드
     */
    @AuditLog(action = "TOKEN_REISSUE", targetType = "AUTH")
    public TokenResponse reissueToken(String refreshToken) {
        // 1. Refresh Token 자체의 만료 및 위변조 여부 검증
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new RedisException("만료되거나 유효하지 않은 Refresh Token입니다. 다시 로그인해 주세요.");
        }

        // 2. 토큰에서 유저 ID 추출 (JwtTokenProvider에 주입해둔 getUserId 메서드 사용)
        String userId = tokenProvider.getUserId(refreshToken);

        // 3. Redis에서 해당 유저의 RT 조회
        String redisKey = "RT:" + userId;
        String savedRefreshToken = redisTemplate.opsForValue().get(redisKey);

        // 4. Redis 토큰 탈락 확인 및 클라이언트가 보낸 토큰과 일치하는지 정합성 검증
        if (savedRefreshToken == null || !savedRefreshToken.equals(refreshToken)) {
            throw new RedisException("토큰 정보가 일치하지 않거나 이미 로그아웃된 계정입니다.");
        }

        // 5. 최신 권한(Role) 정보를 매핑하기 위해 DB 유저 조회
        Auth auth = repository.findByUserId(userId);
        AuditContext.setActor(auth);
        if (auth == null) {
            throw new UserNotExistException("존재하지 않는 유저입니다.");
        }

        // 6. 갱신된 Access Token과 새로운 Refresh Token 세트 생성 (RTR 보안 전략 적용)
        TokenResponse newTokens = tokenProvider.createToken(auth.getUserId(), auth.getRole().name());

        // 7. Redis 토큰 교체 및 만료 시간(14일) 타이머 초기화
        try {
            redisTemplate.opsForValue().set(redisKey, newTokens.getRefreshToken(), Duration.ofDays(14));
        } catch (Exception e) {
            throw new RedisException("Redis 갱신 중 오류가 발생했습니다.");
        }

        return newTokens;
    }

}
