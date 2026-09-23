package dev.bum.auth_service.exception;

import java.time.LocalDate;

public class BlacklistedUserException extends RuntimeException {
    private final LocalDate blacklistedUntil;

    public BlacklistedUserException(LocalDate blacklistedUntil) {
        super(blacklistedUntil == null
                ? "무기한 차단된 계정입니다."
                : blacklistedUntil + "까지 차단된 계정입니다.");
        this.blacklistedUntil = blacklistedUntil;
    }

    public LocalDate getBlacklistedUntil() {
        return blacklistedUntil;
    }
}
