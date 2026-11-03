package dev.bum.auth_service.service;

import dev.bum.auth_service.exception.*;
import dev.bum.auth_service.jpa.Auth;
import dev.bum.auth_service.jpa.AuthJpaRepository;
import dev.bum.auth_service.jpa.UserLoginLock;
import dev.bum.auth_service.jpa.UserLoginLockJpaRepository;
import dev.bum.common.service.user.user.enums.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    // 인증 데이터를 조회하고 저장하는 레포지토리.
    private final AuthJpaRepository authJpaRepository;
    // 사용자 로그인 잠금 데이터를 조회하고 저장하는 레포지토리.
    private final UserLoginLockJpaRepository loginLocks;
    // 비밀번호를 암호화하고 일치 여부를 확인하는 인코더.
    private final PasswordEncoder passwordEncoder;

    /**
     * 입력한 비밀번호가 저장된 값과 일치하는지 검증한다.
     */
    // 로그인 실패 예외로 바깥 트랜잭션이 롤백되어도 실패 횟수와 잠금은 커밋한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW,
            noRollbackFor = {PasswordIncorrectException.class, UserLoginLockedException.class})
    public void validatePassword(Long authId, String password) {

        // 잠금 행이 아직 없어도 최초 생성과 비밀번호 재설정을 계정별로 직렬화한다.
        Auth auth = authJpaRepository.findByIdForUpdate(authId)
                .orElseThrow(() -> new UserNotExistException("존재하지 않는 사용자입니다."));

        UserLoginLock loginLock = loginLocks.findByAuthId(authId)
                .orElseGet(() -> loginLocks.save(UserLoginLock.builder().authId(authId).build()));

        if (loginLock.isLocked()) {
            throw new UserLoginLockedException();
        }

        if (!passwordEncoder.matches(password, auth.getPassword())) {
            loginLock.recordFailure();

            if (loginLock.isLocked()) {
                throw new UserLoginLockedException();
            }

            throw new PasswordIncorrectException("사용자 정보가 일치하지 않습니다.");
        }

        if (auth.getStatus() != UserStatus.ACTIVE) {
            throw new WithdrawnUserException();
        }

        if (auth.isCurrentlyBlacklisted()) {
            throw new BlacklistedUserException(auth.getBlacklistedUntil());
        }

        // 잠기기 전에 로그인 성공 시, 연속 실패 횟수를 초기화한다.
        loginLock.recordSuccess();
    }
}
