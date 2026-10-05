package dev.bum.auth_service.service;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/** 저장된 Refresh Token이 요청 토큰과 일치할 때만 변경한다. */
final class RefreshTokenScripts {
    private RefreshTokenScripts() {}

    // KEYS: Refresh Token 키, 인증 상태 키. ARGV: 기존 토큰, 새 토큰, TTL(ms), 현재 상태.
    // 1=교체 성공, 0=키가 없거나 요청 토큰 불일치. 비교 실패 시 값과 TTL을 보존한다.
    static final DefaultRedisScript<Long> ROTATE = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[2]) ~= ARGV[4] then return 0 end
            if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('psetex', KEYS[1], ARGV[3], ARGV[2])
            return 1
            """, Long.class);

    // 로그인 도중 보안 이벤트가 처리되면 이전 버전의 세션을 저장하지 않는다.
    static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[2]) ~= ARGV[3] then return 0 end
            redis.call('psetex', KEYS[1], ARGV[2], ARGV[1])
            return 1
            """, Long.class);

    // 1=삭제 성공, 0=키가 없거나 요청 토큰 불일치. 이전 토큰으로 새 세션을 삭제하지 않는다.
    static final DefaultRedisScript<Long> DELETE = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('del', KEYS[1])
            return 1
            """, Long.class);
}
