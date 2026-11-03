package dev.bum.common.security;

import lombok.Builder;

@Builder
public record TokenState(
        // 토큰 무효화 여부를 판별하는 버전.
        long version,

        // 활성화 여부.
        boolean active,

        // 사용자 권한.
        String role
) {

    // 토큰 버전, 활성 상태와 권한을 저장용 문자열로 직렬화한다.
    public String encode() {

        return version + ":" + (active ? "1" : "0") + ":" + role;
    }

    // 저장된 문자열을 검증하고 토큰 상태 객체로 복원한다.
    public static TokenState decode(String value) {

        String[] parts = value.split(":", -1);

        if (parts.length != 3 || !(parts[1].equals("0") || parts[1].equals("1"))) {
            throw new IllegalArgumentException("Invalid token state");
        }

        long version = Long.parseLong(parts[0]);

        if (version < 1) throw new IllegalArgumentException("Invalid token version");

        return TokenState.builder()
                .version(version)
                .active(parts[1].equals("1"))
                .role(parts[2])
                .build();
    }
}
