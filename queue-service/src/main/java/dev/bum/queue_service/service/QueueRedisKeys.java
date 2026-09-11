package dev.bum.queue_service.service;

import org.springframework.stereotype.Component;

@Component
public class QueueRedisKeys {

    public String waitingKey(Long eventId) {
        return "queue:event:" + eventId + ":waiting";
    }

    public String waitingTokenKey(String token) {
        return "queue:waiting-token:" + token;
    }

    public String userSessionsKey(String userId) {
        return "queue:user-sessions:" + userId;
    }

    public String waitingSessionMember(String waitingToken) {
        return "waiting:" + waitingToken;
    }

    public String waitingExpiryKey(Long eventId) {
        return "queue:event:" + eventId + ":waiting-expiry";
    }

    public String activeKey(Long eventId) {
        return "queue:event:" + eventId + ":active";
    }

    public String activeTokenKey(String activeToken) {
        return "queue:active-token:" + activeToken;
    }

    public String activeSessionMember(String activeToken) {
        return "active:" + activeToken;
    }

}
