package dev.bum.common.security;

import lombok.Builder;

@Builder
public record TokenState(long version, boolean active, String role) {
    public String encode() {
        return version + ":" + (active ? "1" : "0") + ":" + role;
    }

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
