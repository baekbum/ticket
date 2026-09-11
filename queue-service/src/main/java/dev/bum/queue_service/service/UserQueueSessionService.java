package dev.bum.queue_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserQueueSessionService {

    private final StringRedisTemplate redisTemplate;
    private final QueueRedisKeys keys;

    public void addWaiting(String userId, String waitingToken, long expiresAt) {
        redisTemplate.opsForZSet().add(
                keys.userSessionsKey(userId),
                keys.waitingSessionMember(waitingToken),
                expiresAt
        );
    }

    public void removeWaiting(String userId, String waitingToken) {
        if (StringUtils.hasText(userId)) {
            redisTemplate.opsForZSet().remove(keys.userSessionsKey(userId), keys.waitingSessionMember(waitingToken));
        }
    }

    public void removeActive(String userId, String activeToken) {
        if (StringUtils.hasText(userId)) {
            redisTemplate.opsForZSet().remove(keys.userSessionsKey(userId), keys.activeSessionMember(activeToken));
        }
    }

    public Long count(String userId) {
        Long count = redisTemplate.opsForZSet().zCard(keys.userSessionsKey(userId));
        return count == null ? 0L : count;
    }

    public QueueUserSession activeSession(String userId) {
        Set<String> sessionMembers = redisTemplate.opsForZSet().range(keys.userSessionsKey(userId), 0, -1);
        if (sessionMembers == null || sessionMembers.isEmpty()) {
            return null;
        }

        return sessionMembers.stream()
                .filter(sessionMember -> sessionMember.startsWith("active:"))
                .findFirst()
                .map(this::toUserSession)
                .orElse(null);
    }

    public QueueUserSession firstExpiringSession(String userId) {
        Set<String> sessionMembers = redisTemplate.opsForZSet().range(keys.userSessionsKey(userId), 0, 0);
        if (sessionMembers == null || sessionMembers.isEmpty()) {
            return null;
        }

        return toUserSession(sessionMembers.iterator().next());
    }

    /**
     * 만료된 토큰이 있는지 확인하여 있으면 유저 세션 zSet에서 제거한다.
     * @param userId
     */
    public void prune(String userId) {
        Set<String> sessionMembers = redisTemplate.opsForZSet().range(keys.userSessionsKey(userId), 0, -1);
        if (sessionMembers == null || sessionMembers.isEmpty()) {
            return;
        }

        List<String> memberList = new ArrayList<>(sessionMembers);
        List<String> tokenKeys = memberList.stream()
                .map(this::tokenKey)
                .toList();
        List<String> tokenValues = redisTemplate.opsForValue().multiGet(tokenKeys);

        if (tokenValues == null) {
            return;
        }

        for (int i = 0; i < memberList.size(); i++) {
            String tokenValue = i < tokenValues.size() ? tokenValues.get(i) : null;
            if (!StringUtils.hasText(tokenValue)) {
                redisTemplate.opsForZSet().remove(keys.userSessionsKey(userId), memberList.get(i));
            }
        }
    }

    private QueueUserSession toUserSession(String sessionMember) {
        if (sessionMember.startsWith("waiting:")) {
            return new QueueUserSession(
                    QueueSessionType.WAITING,
                    sessionMember.substring("waiting:".length()),
                    expiresInSeconds(keys.waitingTokenKey(sessionMember.substring("waiting:".length())))
            );
        }

        if (sessionMember.startsWith("active:")) {
            return new QueueUserSession(
                    QueueSessionType.ACTIVE,
                    sessionMember.substring("active:".length()),
                    expiresInSeconds(keys.activeTokenKey(sessionMember.substring("active:".length())))
            );
        }

        return null;
    }

    private String tokenKey(String sessionMember) {
        if (sessionMember.startsWith("waiting:")) {
            return keys.waitingTokenKey(sessionMember.substring("waiting:".length()));
        }

        if (sessionMember.startsWith("active:")) {
            return keys.activeTokenKey(sessionMember.substring("active:".length()));
        }

        return sessionMember;
    }

    private long expiresInSeconds(String tokenKey) {
        Long expiresInSeconds = redisTemplate.getExpire(tokenKey);
        return expiresInSeconds == null ? -1L : expiresInSeconds;
    }
}
