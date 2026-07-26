package dev.bum.queue_service.controller;

import dev.bum.common.service.queue.dto.QueueBulkStatusRequest;
import dev.bum.common.service.queue.dto.QueueEnterResponse;
import dev.bum.common.service.queue.dto.QueueStatusResponse;
import dev.bum.common.service.queue.dto.QueueValidateRequest;
import dev.bum.common.service.queue.dto.QueueValidateResponse;
import dev.bum.queue_service.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/manage/queue/test")
@RequiredArgsConstructor
public class QueueTestManagementController {

    private final QueueService queueService;
    private final StringRedisTemplate redisTemplate;

    @PostMapping("/events/{eventId}/enter")
    public ResponseEntity<QueueEnterResponse> enter(
            @PathVariable Long eventId,
            @RequestParam String userId
    ) {
        return ResponseEntity.ok(queueService.enter(eventId, userId));
    }

    @GetMapping("/events/{eventId}/status")
    public ResponseEntity<QueueStatusResponse> status(
            @PathVariable Long eventId,
            @RequestParam String userId
    ) {
        return ResponseEntity.ok(queueService.status(eventId, userId));
    }

    @PostMapping("/events/{eventId}/statuses")
    public ResponseEntity<List<QueueStatusResponse>> statuses(
            @PathVariable Long eventId,
            @RequestBody QueueBulkStatusRequest request
    ) {
        return ResponseEntity.ok(queueService.statuses(eventId, request.userIds()));
    }

    @PostMapping("/validate")
    public ResponseEntity<QueueValidateResponse> validate(@RequestBody QueueValidateRequest request) {
        return ResponseEntity.ok(queueService.validate(request));
    }

    @PostMapping("/events/{eventId}/complete")
    public ResponseEntity<String> complete(
            @PathVariable Long eventId,
            @RequestParam String userId,
            @RequestParam String token
    ) {
        boolean completed = queueService.complete(eventId, userId, token);
        return completed
                ? ResponseEntity.ok("대기열 active 토큰을 완료 처리했습니다.")
                : ResponseEntity.badRequest().body("유효하지 않은 대기열 토큰입니다.");
    }

    @DeleteMapping("/events/{eventId}")
    public ResponseEntity<String> clearEventQueue(@PathVariable Long eventId) {
        String waitingKey = waitingKey(eventId);
        String activeKey = activeKey(eventId);
        List<String> keys = new ArrayList<>();
        keys.add(waitingKey);
        keys.add(activeKey);

        Set<String> activeTokens = redisTemplate.opsForZSet().range(activeKey, 0, -1);
        if (activeTokens != null) {
            activeTokens.stream()
                    .map(this::tokenKey)
                    .forEach(keys::add);
        }

        Long deleted = redisTemplate.delete(keys);
        return ResponseEntity.ok(String.format("이벤트 %d번 대기열 Redis key %d건을 삭제했습니다.", eventId, deleted == null ? 0 : deleted));
    }

    private String waitingKey(Long eventId) {
        return "queue:event:" + eventId + ":waiting";
    }

    private String activeKey(Long eventId) {
        return "queue:event:" + eventId + ":active";
    }

    private String tokenKey(String token) {
        return "queue:token:" + token;
    }
}
