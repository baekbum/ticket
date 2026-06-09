package dev.bum.auth_service.service;

import dev.bum.auth_service.exception.PasswordIncorrectException;
import dev.bum.auth_service.exception.RedisException;
import dev.bum.auth_service.jpa.Auth;
import dev.bum.auth_service.jpa.AuthRepository;
import dev.bum.auth_service.vo.LoginInfo;
import dev.bum.common.dto.TokenDto;
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
    public void validateInfo(LoginInfo info) {
        Auth auth = findByUserId(info.getUserId());

        comparePassword(info, auth);
    }

    /**
     * 토큰을 발급하는 메서드.
     * @param info
     * @return
     */
    @Transactional(readOnly = true)
    public TokenDto LoginAndCreateToken(LoginInfo info) {
        log.info("login info : {}", info.toString());
        Auth auth = findByUserId(info.getUserId());

        log.info("id : {}", auth.getId());
        log.info("user id : {}", auth.getUserId());
        log.info("user password : {}", auth.getPassword());
        log.info("user role : {}", auth.getRole());

        // 비밀번호 검증
        comparePassword(info, auth);

        TokenDto tokens = tokenProvider.createToken(auth.getUserId(), auth.getRole().name());

        addRefreshTokenToRedis(auth.getUserId(), tokens.getRefreshToken());

        return tokens;
    }

    private void comparePassword(LoginInfo info, Auth auth) {
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

}
