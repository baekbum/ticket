package dev.bum.auth_service.service;

import dev.bum.auth_service.audit.AuditLog;
import dev.bum.auth_service.audit.AuditContext;
import dev.bum.auth_service.exception.BlacklistedUserException;
import dev.bum.auth_service.exception.PasswordIncorrectException;
import dev.bum.auth_service.exception.RedisException;
import dev.bum.auth_service.exception.UserNotExistException;
import dev.bum.auth_service.exception.WithdrawnUserException;
import dev.bum.auth_service.jpa.Auth;
import dev.bum.auth_service.jpa.AuthRepository;
import dev.bum.common.error.ErrorCode;
import dev.bum.common.jwt.dto.TokenResponse;
import dev.bum.common.service.auth.dto.LoginRequest;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenState;
import dev.bum.common.security.TokenStateStore;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.user.user.enums.UserStatus;
import dev.bum.common.service.user.user.enums.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository repository;
    private final LoginAttemptService loginAttemptService;
    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final TokenStateStore tokenStateStore;

    /**
     * 토큰을 발급하는 메서드.
     * @param info
     * @return
     */
    // 실패 횟수 저장용 트랜잭션을 기다리는 동안 외부 DB 연결을 점유하지 않는다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @AuditLog(action = "LOGIN", targetType = "AUTH")
    public TokenResponse LoginAndCreateToken(LoginRequest info) {
        return loginAndCreateToken(info, UserRole.ROLE_USER);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @AuditLog(action = "LOGIN", targetType = "AUTH")
    public TokenResponse adminLoginAndCreateToken(LoginRequest info) {
        return loginAndCreateToken(info, UserRole.ROLE_ADMIN);
    }

    private TokenResponse loginAndCreateToken(LoginRequest info, UserRole requiredRole) {
        info.setUserId(normalizeUserId(info.getUserId()));
        log.info("Login attempt. userId={}", info.getUserId());
        Auth auth = findByUserId(info.getUserId());
        AuditContext.setActor(auth);

        // 인자로 들어온 권한과 실제 사용자의 권한이 다를 경우 에러를 발생시킨다.
        if (auth.getRole() != requiredRole) {
            throw new PasswordIncorrectException(ErrorCode.LOGIN_FAILED.getMessage());
        }

        log.info("id : {}", auth.getId());
        log.info("user id : {}", auth.getUserId());
        log.info("user role : {}", auth.getRole());

        // 비밀번호 검증
        comparePassword(info, auth);

        if (auth.getStatus() != UserStatus.ACTIVE) {
            throw new WithdrawnUserException();
        }

        if (auth.isCurrentlyBlacklisted()) {
            throw new BlacklistedUserException(auth.getBlacklistedUntil());
        }

        TokenResponse tokens = tokenProvider.createToken(auth.getUserId(), auth.getRole().name(), auth.getTokenVersion());

        publishState(auth.getUserId(), stateOf(auth));
        addRefreshTokenToRedis(auth, tokens.getRefreshToken());

        return tokens;
    }

    private void comparePassword(LoginRequest info, Auth auth) {
        loginAttemptService.validatePassword(auth.getId(), info.getPassword());
    }

    private Auth findByUserId(String userId) {
        return repository.findByUserId(normalizeUserId(userId));
    }


    /**
     * refresh 토큰을 redis에 저장
     * @param auth
     * @param refreshToken
     */
    private void addRefreshTokenToRedis(Auth auth, String refreshToken) {
        String userId = auth.getUserId();
        String redisKey = buildRefreshTokenKey(userId);
        try {
            Long result = redisTemplate.execute(RefreshTokenScripts.SAVE,
                    List.of(redisKey, TokenStateStore.key(userId)),
                    refreshToken, String.valueOf(Duration.ofDays(14).toMillis()), stateOf(auth).encode());
            validateTokenMutationResult(result);
        } catch (DataAccessException e) {
            log.error("[REDIS-ERROR] Refresh Token 저장 실패. operation=set, keyPrefix=RT, redisKey={}, userId={}",
                    redisKey, userId, e);
            throw new RedisException("Redis 오류 발생");
        }
    }

    /**
     * 카프카 토픽에서 데이터를 받아 유저를 추가하는 메서드
     * @param event
     */
    public void insertUserTopic(UserDtoForEvent event) {
        event.setUserId(normalizeUserId(event.getUserId()));
        log.info("[유저 추가] userId={}", event.getUserId());
        repository.insert(event);
        Auth auth = repository.findByUserId(event.getUserId());
        TokenState previous = tokenStateStore.get(auth.getUserId());
        if (previous != null) auth.startAfterPreviousAccount(previous.version());
        publishAfterCommit(auth.getUserId(), stateOf(auth));
    }

    /**
     * 카프카 토픽에서 데이터를 받아 유저 정보를 수정하는
     * @param event
     */
    public void updateUserTopic(UserDtoForEvent event) {
        event.setUserId(normalizeUserId(event.getUserId()));
        log.info("[유저 수정] userId={}", event.getUserId());
        Auth auth = repository.update(event);
        publishAfterCommit(auth.getUserId(), stateOf(auth));
    }

    /**
     * 카프카 토픽에서 데이터를 받아 유저를 삭제하는 메서드
     * @param event
     */
    public void deleteUserTopic(UserDtoForEvent event) {
        event.setUserId(normalizeUserId(event.getUserId()));
        log.info("[유저 삭제] userId={}", event.getUserId());
        try {
            Auth auth = repository.findByUserId(event.getUserId());
            // 삭제 이벤트가 재전달돼도 같은 ID로 새로 가입한 계정은 삭제하지 않는다.
            if (event.getId() != null && !event.getId().equals(auth.getId())) return;
            repository.delete(event.getUserId());
            publishAfterCommit(auth.getUserId(), TokenState.builder()
                    .version(Math.addExact(auth.getTokenVersion(), 1L))
                    .active(false)
                    .role(auth.getRole().name())
                    .build());
        } catch (UserNotExistException e) {
            // DB 삭제 후 Redis 갱신 실패로 재전달된 경우에도 폐기를 완료한다.
            TokenState current = tokenStateStore.get(event.getUserId());
            if (current != null) {
                publishAfterCommit(event.getUserId(), TokenState.builder()
                        .version(current.active() ? Math.addExact(current.version(), 1L) : current.version())
                        .active(false)
                        .role(current.role())
                        .build());
            }
        }
    }

    /**
     * Refresh Token을 활용해 Access/Refresh Token 세트를 재발급(갱신)하는 메서드
     */
    @AuditLog(action = "TOKEN_REISSUE", targetType = "AUTH")
    public TokenResponse reissueToken(String refreshToken) {
        // 1. Refresh Token 자체의 만료 및 위변조 여부 검증
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new RedisException(ErrorCode.REFRESH_TOKEN_INVALID, "만료되거나 유효하지 않은 Refresh Token입니다. 다시 로그인해 주세요.");
        }

        if (!"refresh".equals(tokenProvider.getTokenType(refreshToken))) {
            throw new RedisException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh Token이 필요합니다.");
        }

        // 2. 토큰에서 유저 ID 추출 (JwtTokenProvider에 주입해둔 getUserId 메서드 사용)
        String userId = tokenProvider.getUserId(refreshToken);

        // 3. 사전 검증을 위해 Redis 토큰을 조회한다. 실제 교체 시 Lua에서 다시 비교한다.
        String redisKey = buildRefreshTokenKey(userId);
        String savedRefreshToken;
        try {
            savedRefreshToken = redisTemplate.opsForValue().get(redisKey);
        } catch (DataAccessException e) {
            log.error("[REDIS-ERROR] Refresh Token 조회 실패. operation=get, keyPrefix=RT, redisKey={}, userId={}",
                    redisKey, userId, e);
            throw new RedisException("Redis 조회 중 오류가 발생했습니다.");
        }

        // 4. Redis 토큰 탈락 확인 및 클라이언트가 보낸 토큰과 일치하는지 정합성 검증
        if (savedRefreshToken == null || !savedRefreshToken.equals(refreshToken)) {
            throw new RedisException(ErrorCode.REFRESH_TOKEN_MISMATCH, "토큰 정보가 일치하지 않거나 이미 로그아웃된 계정입니다.");
        }

        // 5. 최신 권한(Role) 정보를 매핑하기 위해 DB 유저 조회
        Auth auth = repository.findByUserId(userId);
        AuditContext.setActor(auth);
        if (auth == null) {
            throw new UserNotExistException("존재하지 않는 유저입니다.");
        }
        if (auth.getStatus() != UserStatus.ACTIVE) {
            throw new RedisException(ErrorCode.REFRESH_TOKEN_INVALID, "탈퇴한 계정입니다. 다시 로그인해 주세요.");
        }

        // 6. 갱신된 Access Token과 새로운 Refresh Token 세트 생성 (RTR 보안 전략 적용)
        if (auth.isCurrentlyBlacklisted()) {
            throw new BlacklistedUserException(auth.getBlacklistedUntil());
        }

        if (tokenProvider.getTokenVersion(refreshToken) != auth.getTokenVersion()) {
            throw new RedisException(ErrorCode.REFRESH_TOKEN_MISMATCH, "폐기된 토큰입니다. 다시 로그인해 주세요.");
        }

        TokenResponse newTokens = tokenProvider.createToken(auth.getUserId(), auth.getRole().name(), auth.getTokenVersion());

        // 7. 요청 토큰과 일치할 때만 교체하고 TTL을 초기화한다. 동시 요청 중 하나만 성공한다.
        try {
            Long result = redisTemplate.execute(RefreshTokenScripts.ROTATE, List.of(redisKey, TokenStateStore.key(userId)),
                    refreshToken, newTokens.getRefreshToken(), String.valueOf(Duration.ofDays(14).toMillis()), stateOf(auth).encode());
            validateTokenMutationResult(result);
        } catch (DataAccessException e) {
            log.error("[REDIS-ERROR] Refresh Token 갱신 실패. operation=rotate, keyPrefix=RT, redisKey={}, userId={}",
                    redisKey, userId, e);
            throw new RedisException("Redis 갱신 중 오류가 발생했습니다.");
        }

        log.info("새로운 refreshToken을 발급하였습니다.");

        return newTokens;
    }

    @AuditLog(action = "LOGOUT", targetType = "AUTH")
    public void logout(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new RedisException(ErrorCode.REFRESH_TOKEN_INVALID, "유효하지 않은 Refresh Token입니다.");
        }

        String userId = tokenProvider.getUserId(refreshToken);
        String redisKey = buildRefreshTokenKey(userId);
        String savedRefreshToken;
        try {
            savedRefreshToken = redisTemplate.opsForValue().get(redisKey);
        } catch (DataAccessException e) {
            log.error("[REDIS-ERROR] Refresh Token 조회 실패. operation=get, keyPrefix=RT, redisKey={}, userId={}",
                    redisKey, userId, e);
            throw new RedisException("Redis 조회 중 오류가 발생했습니다.");
        }

        if (savedRefreshToken == null || !savedRefreshToken.equals(refreshToken)) {
            throw new RedisException(ErrorCode.REFRESH_TOKEN_MISMATCH, "Refresh Token 정보가 일치하지 않습니다.");
        }

        Auth auth = repository.findByUserId(userId);
        AuditContext.setActor(auth);
        if (auth == null) {
            throw new UserNotExistException("존재하지 않는 사용자입니다.");
        }

        try {
            // 사전 조회 후 토큰이 교체됐더라도 이전 요청으로 새 토큰을 삭제하지 않는다.
            Long result = redisTemplate.execute(RefreshTokenScripts.DELETE, List.of(redisKey), refreshToken);
            validateTokenMutationResult(result);
        } catch (DataAccessException e) {
            log.error("[REDIS-ERROR] Refresh Token 삭제 실패. operation=delete, keyPrefix=RT, redisKey={}, userId={}",
                    redisKey, userId, e);
            throw new RedisException("Redis Refresh Token 삭제 중 오류가 발생했습니다.");
        }
    }

    private TokenState stateOf(Auth auth) {
        return TokenState.builder()
                .version(auth.getTokenVersion())
                .active(auth.getStatus() == UserStatus.ACTIVE && !auth.isCurrentlyBlacklisted())
                .role(auth.getRole().name())
                .build();
    }

    private void publishAfterCommit(String userId, TokenState state) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishState(userId, state);
                }
            });
        } else {
            publishState(userId, state);
        }
    }

    private void publishState(String userId, TokenState state) {
        try {
            tokenStateStore.publish(userId, state);
        } catch (RuntimeException e) {
            // DB 커밋 이후 실패해도 Kafka가 재전달하면 현재 버전을 다시 게시한다.
            throw new RedisException("인증 상태 갱신에 실패했습니다.");
        }
    }

    private String buildRefreshTokenKey(String userId) {
        return "RT:" + userId;
    }

    private void validateTokenMutationResult(Long result) {
        if (Long.valueOf(0L).equals(result)) {
            throw new RedisException(ErrorCode.REFRESH_TOKEN_MISMATCH,
                    "Refresh Token이 변경되었거나 이미 로그아웃되었습니다. 다시 로그인해 주세요.");
        }
        if (!Long.valueOf(1L).equals(result)) {
            throw new RedisException("Redis Refresh Token 처리 결과를 확인할 수 없습니다.");
        }
    }

    private String normalizeUserId(String userId) {
        return StringUtils.hasText(userId) ? userId.trim().toLowerCase(Locale.ROOT) : userId;
    }

}
