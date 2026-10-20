package dev.bum.auth_service.service;

import dev.bum.auth_service.jpa.event.ProcessedUserEvent;
import dev.bum.auth_service.jpa.event.ProcessedUserEventJpaRepository;
import dev.bum.common.kafka.enums.TopicEventType;
import dev.bum.common.kafka.user.UserDtoForEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class UserEventIdempotencyService {
    private final ProcessedUserEventJpaRepository repository;

    @Transactional(propagation = Propagation.MANDATORY)
    public UserEventRegistrationResult registerAndValidate(UserDtoForEvent event) {
        if (event.getId() == null
                || !StringUtils.hasText(event.getIdempotencyKey())
                || event.getIdempotencyKey().length() > 100
                || (event.getEventType() != TopicEventType.CREATE
                && event.getEventType() != TopicEventType.UPDATE)) {

            throw new IllegalArgumentException("회원 이벤트의 멱등키 또는 대상 정보가 올바르지 않습니다.");
        }

        String requestHash = fingerprint(event); // 데이터를 가지고 해시 코드를 만듬

        // 같은 회원·멱등키가 있으면 저장을 생략하고 0을 반환한다.
        int inserted = repository.insertIfAbsent(
                event.getIdempotencyKey(), event.getId(), event.getUserId(), event.getEventType().name(), requestHash
        );

        // 신규·기존 이력을 같은 조건으로 잠금 조회한 뒤 요청 내용이 일치하는지 검증한다.
        ProcessedUserEvent recorded = repository.findByAuthIdAndIdempotencyKeyForUpdate(
                event.getId(), event.getIdempotencyKey()
        )
                .orElseThrow(() -> new IllegalStateException("회원 이벤트 처리 이력을 찾을 수 없습니다."));

        if (!recorded.getRequestHash().equals(requestHash)) {
            throw new IllegalArgumentException("같은 회원의 멱등키로 다른 요청 내용을 처리할 수 없습니다.");
        }

        return inserted == 1 ? UserEventRegistrationResult.REGISTERED : UserEventRegistrationResult.DUPLICATE;
    }

    private String fingerprint(UserDtoForEvent event) {
        String[] values = {
                event.getEventType().name(), event.getId().toString(), event.getUserId(), event.getPassword(),
                text(event.getPasswordReset()), event.getRole(), event.getStatus(),
                text(event.getIsBlacklisted()), text(event.getBlacklistedUntil())
        };
        // 길이와 null을 구분해 구분자나 필드 조합이 달라도 같은 입력 문자열이 되지 않게 한다.
        StringBuilder request = new StringBuilder();

        for (String value : values) {
            request.append(value == null ? -1 : value.length()).append(':');
            if (value != null) request.append(value);
        }

        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(request.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("회원 이벤트 검증을 초기화할 수 없습니다.");
        }
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}
