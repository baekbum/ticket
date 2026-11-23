package dev.bum.ticket_service.service.captcha;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.ticket_service.exception.captcha.CaptchaUnavailableException;
import dev.bum.ticket_service.jpa.event.event.EventRepository;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

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

    // 발급 상태 저장과 준비 목록 제거를 함께 처리한다.
    private static final DefaultRedisScript<String> ISSUE_CHALLENGE = new DefaultRedisScript<>("""
            local issued = redis.call('GET', KEYS[2])
            if issued then
                return issued
            end

            local prepared = redis.call('LINDEX', KEYS[1], 0)
            if not prepared then
                return nil
            end

            local captcha = cjson.decode(prepared)
            local now = redis.call('TIME')
            captcha.challengeId = ARGV[1]
            captcha.expiresAt = now[1] * 1000 + math.floor(now[2] / 1000) + tonumber(ARGV[2])
            local value = cjson.encode(captcha)
            redis.call('SET', KEYS[2], value, 'PX', ARGV[2])
            redis.call('LPOP', KEYS[1])
            return value
            """, String.class);

    private final EventRepository events;
    private final QueueAccessService queueAccessService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public Optional<CaptchaChallengeResponse> issue(Long eventId, String userId, String activeToken) {
        if (!events.selectById(eventId).isCaptchaEnabled()) {
            return Optional.empty();
        }

        queueAccessService.validateActiveToken(eventId, userId, activeToken);
        String issuedKey = issuedKey(eventId, userId, activeToken);
        try {
            String value = redis.execute(ISSUE_CHALLENGE, List.of(CaptchaImagePool.KEY, issuedKey),
                    UUID.randomUUID().toString(), Long.toString(CHALLENGE_TTL.toMillis()));

            if (value == null) {
                throw new CaptchaUnavailableException();
            }

            SavedChallenge challenge = objectMapper.readValue(value, SavedChallenge.class);
            return Optional.of(new CaptchaChallengeResponse(challenge.challengeId(), challenge.png(),
                    Instant.ofEpochMilli(challenge.expiresAt())));
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("캡차 발급 실패: eventId={}, errorType={}", eventId, e.getClass().getSimpleName());
            throw new CaptchaUnavailableException();
        }
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
