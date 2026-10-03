package dev.bum.auth_service.exception;

public class UserLoginLockedException extends RuntimeException {
    public UserLoginLockedException() {
        super("비밀번호를 5회 연속 잘못 입력하여 계정이 잠겼습니다. 비밀번호 찾기로 비밀번호를 재설정해 주세요.");
    }
}
