package dev.bum.auth_service.jpa;

import dev.bum.auth_service.exception.UserAlreadyExistException;
import dev.bum.auth_service.exception.UserNotExistException;
import dev.bum.common.kafka.user.UserDtoForEvent;
import dev.bum.common.service.user.user.enums.UserRole;
import dev.bum.common.service.user.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AuthRepositoryImpl implements AuthRepository {

    private final AuthJpaRepository jpaRepository;
    private final UserLoginLockJpaRepository loginLocks;

    @Override
    public boolean insert(UserDtoForEvent event) {
        event.setUserId(normalizeUserId(event.getUserId()));
        Auth existing = jpaRepository.findByUserId(event.getUserId()).orElse(null);
        if (existing != null) {
            // 같은 계정의 CREATE 재전달은 현재 비밀번호·권한을 초기 값으로 되돌리지 않는다.
            if (event.getId() != null && event.getId().equals(existing.getId())) return false;
            throw new UserAlreadyExistException("이미 해당 유저가 존재합니다.");
        }

        Auth auth = Auth.builder()
                .id(event.getId())
                .userId(event.getUserId())
                .password(event.getPassword())
                .role(UserRole.valueOf(event.getRole()))
                .status(event.getStatus() == null ? UserStatus.ACTIVE : UserStatus.valueOf(event.getStatus()))
                .isBlacklisted(event.getIsBlacklisted())
                .blacklistedUntil(event.getBlacklistedUntil())
                .build();

        auth.updateInfo(event);
        jpaRepository.save(auth);
        return true;
    }

    @Override
    public Auth findByUserId(String userId) {
        return jpaRepository.findByUserId(normalizeUserId(userId))
                .orElseThrow(() -> new UserNotExistException("해당 유저를 발견하지 못했습니다."));
    }

    @Override
    public Auth update(UserDtoForEvent event) {
        event.setUserId(normalizeUserId(event.getUserId()));
        Auth auth = jpaRepository.findByUserIdForUpdate(event.getUserId())
                .orElseThrow(() -> new UserNotExistException("해당 유저를 발견하지 못했습니다."));
        if (event.getId() != null && !event.getId().equals(auth.getId())) {
            throw new UserNotExistException("이전 계정의 수정 이벤트입니다.");
        }
        boolean passwordReset = StringUtils.hasText(event.getPassword())
                && (event.getPasswordResetVersion() != null
                ? event.getPasswordResetVersion() > auth.getPasswordResetVersion()
                : Boolean.TRUE.equals(event.getPasswordReset()) && !Objects.equals(auth.getPassword(), event.getPassword()));
        auth.updateInfo(event);
        // 같은 재설정 이벤트가 재전달되면 그 이후에 쌓인 실패 횟수를 지우지 않는다.
        if (passwordReset) {
            loginLocks.findByAuthId(auth.getId()).ifPresent(UserLoginLock::reset);
        }
        return auth;
    }

    @Override
    public void delete(String userId) {
        Auth foundUser = findByUserId(normalizeUserId(userId));

        jpaRepository.delete(foundUser);
    }

    private String normalizeUserId(String userId) {
        return StringUtils.hasText(userId) ? userId.trim().toLowerCase(Locale.ROOT) : userId;
    }
}
