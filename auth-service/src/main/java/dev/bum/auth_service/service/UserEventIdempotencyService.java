package dev.bum.auth_service.service;

import dev.bum.auth_service.jpa.event.ProcessedUserEvent;
import dev.bum.auth_service.jpa.event.ProcessedUserEventJpaRepository;
import dev.bum.auth_service.jpa.event.UserEventVersion;
import dev.bum.auth_service.jpa.event.UserEventVersionJpaRepository;
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
    private final UserEventVersionJpaRepository versionRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public UserEventRegistrationResult registerAndValidate(UserDtoForEvent event) {
        if (event.getId() == null
                || event.getEventVersion() == null || event.getEventVersion() < 1
                || (event.getPasswordResetVersion() != null && (event.getPasswordResetVersion() < 0
                || event.getPasswordResetVersion() > event.getEventVersion()))
                || !StringUtils.hasText(event.getIdempotencyKey())
                || event.getIdempotencyKey().length() > 100
                || event.getEventType() == null) {

            throw new IllegalArgumentException("회원 이벤트의 멱등키 또는 대상 정보가 올바르지 않습니다.");
        }

        String requestHash = fingerprint(event); // 데이터를 가지고 해시 코드를 만듬

        // 같은 회원·멱등키가 있으면 저장을 생략하고 0을 반환한다.
        int inserted = repository.insertIfAbsent(
                event.getIdempotencyKey(), event.getId(), event.getEventVersion(),
                event.getUserId(), event.getEventType().name(), requestHash
        );

        // 신규·기존 이력을 같은 조건으로 잠금 조회한 뒤 요청 내용이 일치하는지 검증한다.
        ProcessedUserEvent recorded = repository.findByAuthIdAndIdempotencyKeyForUpdate(
                event.getId(), event.getIdempotencyKey()
        ).orElseThrow(() -> new IllegalStateException("회원 이벤트 처리 이력을 찾을 수 없습니다."));

        if (!recorded.getRequestHash().equals(requestHash)) {
            throw new IllegalArgumentException("같은 회원의 멱등키로 다른 요청 내용을 처리할 수 없습니다.");
        }

        // 서로 다른 멱등키로 동시에 도착한 이벤트도 회원별 행 잠금 아래 버전을 비교한다.
        versionRepository.insertIfAbsent(event.getId());

        UserEventVersion version = versionRepository.findByAuthIdForUpdate(event.getId())
                .orElseThrow(() -> new IllegalStateException("회원 이벤트 버전 정보를 찾을 수 없습니다."));

        // 과거에 건너뛴 DELETE의 재전달이 최신 회원을 삭제하지 않도록 현재 버전·삭제 여부도 확인한다.
        if (inserted == 0 && event.getEventVersion() == version.getLastEventVersion()
                && (event.getEventType() == TopicEventType.DELETE) == version.isDeleted()) {
            return UserEventRegistrationResult.DUPLICATE;
        }

        if (version.isStale(event.getEventVersion())) return UserEventRegistrationResult.STALE;

        // 실제 회원 반영이 실패하면 이 버전 변경과 처리 이력도 함께 롤백된다.
        version.advance(event.getEventVersion(), event.getEventType() == TopicEventType.DELETE);
        return UserEventRegistrationResult.REGISTERED;
    }

    private String fingerprint(UserDtoForEvent event) {
        String[] values = {
                event.getEventType().name(), event.getId().toString(), text(event.getEventVersion()),
                text(event.getPasswordResetVersion()), event.getUserId(), event.getPassword(),
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
