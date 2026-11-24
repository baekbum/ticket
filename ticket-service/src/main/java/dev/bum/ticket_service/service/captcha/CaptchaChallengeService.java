package dev.bum.ticket_service.service.captcha;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.ticket_service.exception.captcha.CaptchaAttemptLimitException;
import dev.bum.ticket_service.exception.captcha.CaptchaChallengeChangedException;
import dev.bum.ticket_service.exception.captcha.CaptchaChallengeExpiredException;
import dev.bum.ticket_service.exception.captcha.CaptchaRefreshRateLimitedException;
import dev.bum.ticket_service.exception.captcha.CaptchaRequiredException;
import dev.bum.ticket_service.exception.captcha.CaptchaUnavailableException;
import dev.bum.ticket_service.exception.queue.ActiveTokenExpiredException;
import dev.bum.ticket_service.exception.queue.QueueUnavailableException;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaChallengeService {

    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(2);
    private static final Duration REFRESH_WINDOW = Duration.ofMinutes(1);
    private static final int MAX_REFRESHES_PER_WINDOW = 5;
    private static final int MAX_ATTEMPTS_PER_TOKEN = 5;

    // 발급 상태 저장과 준비 목록 제거를 함께 처리한다.
    private static final DefaultRedisScript<String> SAVE_CHALLENGE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[4]) == 1 then
                return 'PASSED'
            end
            local issued = redis.call('GET', KEYS[2])
            local refresh = ARGV[3] == 'true'
            local refreshCount = 0

            if refresh then
                if not issued then
                    return 'EXPIRED'
                end
                refreshCount = tonumber(redis.call('GET', KEYS[3]) or '0')
                if refreshCount >= tonumber(ARGV[4]) then
                    return 'RATE_LIMITED'
                end
            elseif issued then
                return issued
            end

            local prepared = redis.call('LINDEX', KEYS[1], 0)
            if not prepared then
                return 'POOL_EMPTY'
            end

            local captcha = cjson.decode(prepared)
            local now = redis.call('TIME')
            captcha.challengeId = ARGV[1]
            captcha.expiresAt = now[1] * 1000 + math.floor(now[2] / 1000) + tonumber(ARGV[2])
            local value = cjson.encode(captcha)
            redis.call('SET', KEYS[2], value, 'PX', ARGV[2])
            redis.call('LPOP', KEYS[1])
            if refresh then
                if refreshCount == 0 then
                    redis.call('SET', KEYS[3], '1', 'PX', ARGV[5])
                else
                    redis.call('INCR', KEYS[3])
                end
            end
            return value
            """, String.class);

    // 같은 세션에서 검증과 재발급이 동시에 들어와도 현재 문제만 검증한다.
    private static final DefaultRedisScript<String> VERIFY_CHALLENGE = new DefaultRedisScript<>("""
            local now = redis.call('TIME')
            local nowMillis = now[1] * 1000 + math.floor(now[2] / 1000)
            if tonumber(ARGV[3]) <= nowMillis then
                return 'TOKEN_EXPIRED'
            end

            local issued = redis.call('GET', KEYS[1])
            if not issued then
                return 'EXPIRED'
            end
            local captcha = cjson.decode(issued)
            if captcha.challengeId ~= ARGV[1] then
                return 'CHANGED'
            end

            local attempts = tonumber(redis.call('GET', KEYS[3]) or '0')
            local maxAttempts = tonumber(ARGV[4])
            if attempts >= maxAttempts then
                return 'ATTEMPT_LIMIT'
            end

            if captcha.answer ~= ARGV[2] then
                attempts = attempts + 1
                redis.call('SET', KEYS[3], attempts, 'PXAT', ARGV[3])
                if attempts >= maxAttempts then
                    return 'ATTEMPT_LIMIT'
                end
                return 'INCORRECT'
            end

            redis.call('SET', KEYS[2], captcha.challengeId, 'PXAT', ARGV[3])
            redis.call('DEL', KEYS[1], KEYS[3])
            return 'PASSED'
            """, String.class);

    private final EventRepository events;
    private final QueueAccessService queueAccessService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public Optional<CaptchaChallengeResponse> issue(Long eventId, String userId, String activeToken) {
        return getChallenge(eventId, userId, activeToken, false);
    }

    public Optional<CaptchaChallengeResponse> refresh(Long eventId, String userId, String activeToken) {
        return getChallenge(eventId, userId, activeToken, true);
    }

    public void validateSeatOccupation(Long eventId, String userId, String activeToken) {
        if (eventId != null && events.selectById(eventId).isCaptchaEnabled()) {
            requirePassed(eventId, userId, activeToken);
            return;
        }

        queueAccessService.validate(eventId, userId, activeToken);
    }

    public void requirePassed(Long eventId, String userId, String activeToken) {
        if (!StringUtils.hasText(activeToken)) {
            throw new CaptchaRequiredException();
        }

        queueAccessService.validateActiveToken(eventId, userId, activeToken);

        try {
            if (!Boolean.TRUE.equals(redis.hasKey(passedKey(issuedKey(eventId, userId, activeToken))))) {
                throw new CaptchaRequiredException();
            }
        } catch (DataAccessException e) {
            log.warn("캡차 통과 상태 확인 실패: eventId={}, errorType={}", eventId, e.getClass().getSimpleName());
            throw new CaptchaUnavailableException();
        }
    }

    public void closeSession(Long eventId, String userId, String activeToken) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(activeToken)) {
            return;
        }
        String issuedKey = issuedKey(eventId, userId, activeToken);
        try {
            redis.delete(List.of(issuedKey, passedKey(issuedKey)));
        } catch (DataAccessException e) {
            log.warn("캡차 세션 정리 실패: eventId={}, errorType={}", eventId, e.getClass().getSimpleName());
            throw new CaptchaUnavailableException();
        }
    }

    private Optional<CaptchaChallengeResponse> getChallenge(
            Long eventId, String userId, String activeToken, boolean refresh
    ) {
        if (!events.selectById(eventId).isCaptchaEnabled()) {
            return Optional.empty();
        }

        queueAccessService.validateActiveToken(eventId, userId, activeToken);
        String issuedKey = issuedKey(eventId, userId, activeToken);
        try {
            String value = redis.execute(SAVE_CHALLENGE,
                    List.of(CaptchaImagePool.KEY, issuedKey, issuedKey + ":refresh", passedKey(issuedKey)),
                    UUID.randomUUID().toString(), Long.toString(CHALLENGE_TTL.toMillis()), Boolean.toString(refresh),
                    Integer.toString(MAX_REFRESHES_PER_WINDOW), Long.toString(REFRESH_WINDOW.toMillis()));

            if ("PASSED".equals(value)) {
                return Optional.empty();
            }
            if ("POOL_EMPTY".equals(value)) {
                throw new CaptchaUnavailableException();
            }

            if ("EXPIRED".equals(value)) {
                throw new CaptchaChallengeExpiredException();
            }

            if ("RATE_LIMITED".equals(value)) {
                throw new CaptchaRefreshRateLimitedException();
            }

            SavedChallenge challenge = objectMapper.readValue(value, SavedChallenge.class);
            return Optional.of(new CaptchaChallengeResponse(challenge.challengeId(), challenge.png(),
                    Instant.ofEpochMilli(challenge.expiresAt())));
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("캡차 발급 실패: eventId={}, errorType={}", eventId, e.getClass().getSimpleName());
            throw new CaptchaUnavailableException();
        }
    }

    public CaptchaVerificationResponse verify(Long eventId, String userId, String activeToken, UUID challengeId, String answer) {
        if (!events.selectById(eventId).isCaptchaEnabled()) {
            return new CaptchaVerificationResponse(true);
        }

        Instant expiresAt = queueAccessService.validateActiveToken(eventId, userId, activeToken);
        if (expiresAt == null) {
            throw new QueueUnavailableException();
        }
        String issuedKey = issuedKey(eventId, userId, activeToken);
        try {
            String result = redis.execute(VERIFY_CHALLENGE,
                    List.of(issuedKey, passedKey(issuedKey), issuedKey + ":attempts"),
                    challengeId.toString(), answer, Long.toString(expiresAt.toEpochMilli()),
                    Integer.toString(MAX_ATTEMPTS_PER_TOKEN));
            return switch (result) {
                case "PASSED" -> new CaptchaVerificationResponse(true);
                case "INCORRECT" -> new CaptchaVerificationResponse(false);
                case "CHANGED" -> throw new CaptchaChallengeChangedException();
                case "EXPIRED" -> throw new CaptchaChallengeExpiredException();
                case "ATTEMPT_LIMIT" -> throw new CaptchaAttemptLimitException();
                case "TOKEN_EXPIRED" -> throw new ActiveTokenExpiredException();
                default -> throw new CaptchaUnavailableException();
            };
        } catch (DataAccessException e) {
            log.warn("캡차 검증 실패: eventId={}, errorType={}", eventId, e.getClass().getSimpleName());
            throw new CaptchaUnavailableException();
        }
    }

    private static String passedKey(String issuedKey) {
        return issuedKey + ":passed";
    }

    static String issuedKey(Long eventId, String userId, String activeToken) {
        String binding = eventId + "\0" + userId + "\0" + activeToken;

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(binding.getBytes(StandardCharsets.UTF_8));
            return "ticket:captcha:issued:v1:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record SavedChallenge(UUID challengeId, String png, String answer, long expiresAt) {
    }
}
