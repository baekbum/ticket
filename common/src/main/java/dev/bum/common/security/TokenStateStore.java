package dev.bum.common.security;

public interface TokenStateStore {
    TokenState get(String userId);
    void publish(String userId, TokenState state);

    static String key(String userId) {
        return "AUTH:STATE:" + userId;
    }
}
