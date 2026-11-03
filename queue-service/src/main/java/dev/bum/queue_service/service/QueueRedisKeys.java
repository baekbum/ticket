package dev.bum.queue_service.service;

import org.springframework.stereotype.Component;

@Component
public class QueueRedisKeys {

    /**
     * 공연별 대기열 정책 Redis 키를 구성한다.
     */
    public String policyKey(Long eventId) {

        return "queue:event:" + eventId + ":policy"; }

    /**
     * 공연별 대기열 Redis 키를 구성한다.
     */
    public String waitingKey(Long eventId) {

        return "queue:event:" + eventId + ":waiting";
    }

    /**
     * 대기 토큰을 저장할 Redis 키를 구성한다.
     */
    public String waitingTokenKey(String token) {

        return "queue:waiting-token:" + token;
    }

    /**
     * 사용자별 대기열 세션 Redis 키를 구성한다.
     */
    public String userSessionsKey(String userId) {

        return "queue:user-sessions:" + userId;
    }

    /**
     * 사용자 세션에 저장할 대기 항목을 구성한다.
     */
    public String waitingSessionMember(String waitingToken) {

        return "waiting:" + waitingToken;
    }

    /**
     * 대기 토큰 만료 시각을 관리할 Redis 키를 구성한다.
     */
    public String waitingExpiryKey(Long eventId) {

        return "queue:event:" + eventId + ":waiting-expiry";
    }

    /**
     * 공연별 입장 토큰 목록 Redis 키를 구성한다.
     */
    public String activeKey(Long eventId) {

        return "queue:event:" + eventId + ":active";
    }

    /**
     * 입장 토큰을 저장할 Redis 키를 구성한다.
     */
    public String activeTokenKey(String activeToken) {

        return "queue:active-token:" + activeToken;
    }

    /**
     * 대기 토큰에서 입장 토큰을 찾을 Redis 키를 구성한다.
     */
    public String waitingActiveMappingKey(String waitingToken) {

        return "queue:waiting-active:" + waitingToken;
    }

    /**
     * 입장 토큰에서 대기 토큰을 찾을 Redis 키를 구성한다.
     */
    public String activeWaitingMappingKey(String activeToken) {

        return "queue:active-waiting:" + activeToken;
    }

    /**
     * 사용자 세션에 저장할 입장 항목을 구성한다.
     */
    public String activeSessionMember(String activeToken) {

        return "active:" + activeToken;
    }
}
