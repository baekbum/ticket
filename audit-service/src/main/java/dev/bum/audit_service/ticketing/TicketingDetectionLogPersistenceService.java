package dev.bum.audit_service.ticketing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.kafka.ticketing.TicketingDetectionLogEvent;
import dev.bum.common.service.audit.dto.TicketingDetectionLogCondRequest;
import dev.bum.common.service.audit.dto.TicketingDetectionLogDetailResponse;
import dev.bum.common.service.audit.dto.TicketingDetectionLogListResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TicketingDetectionLogPersistenceService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));

    private final TicketingDetectionLogJpaRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(TicketingDetectionLogEvent event) {

        TicketingDetectionLogEntity entry = TicketingDetectionLogEntity.from(event);
        repository.insertIfAbsent(
                entry.getEventId(),
                entry.getUserId(),
                entry.getSessionKeyHash(),
                entry.getDetectionType().name(),
                entry.getTraceId(),
                serializeEvidence(entry.getEvidence()),
                OffsetDateTime.ofInstant(entry.getOccurredAt(), ZoneOffset.UTC)
        );
    }

    @Transactional(readOnly = true)
    public TicketingDetectionLogDetailResponse selectById(Long id) {
        TicketingDetectionLogEntity log = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "해당 티켓팅 탐지 로그가 존재하지 않습니다."));
        return new TicketingDetectionLogDetailResponse(log.getId(), log.getEventId(), log.getUserId(),
                log.getDetectionType(), log.getOccurredAt(), log.getTraceId(), log.getSessionKeyHash(), log.getEvidence());
    }

    @Transactional(readOnly = true)
    public CustomPageResponse<TicketingDetectionLogListResponse> selectByCond(TicketingDetectionLogCondRequest cond) {
        TicketingDetectionLogCondRequest criteria = cond == null
                ? new TicketingDetectionLogCondRequest(null, null, null, null, null, null, null)
                : cond;
        if (criteria.occurredFrom() != null && criteria.occurredTo() != null
                && criteria.occurredFrom().isAfter(criteria.occurredTo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 시작 시각은 종료 시각보다 늦을 수 없습니다.");
        }

        int page = criteria.page() == null ? 0 : Math.max(0, criteria.page());
        int size = criteria.size() == null || criteria.size() <= 0 ? DEFAULT_SIZE : Math.min(criteria.size(), MAX_SIZE);
        PageRequest pageable = PageRequest.of(page, size, NEWEST_FIRST);
        if (pageable.getOffset() > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회할 페이지 번호가 범위를 벗어났습니다.");
        }

        Page<TicketingDetectionLogListResponse> result = repository.findAll(makeSpec(criteria), pageable)
                .map(log -> new TicketingDetectionLogListResponse(
                        log.getId(), log.getEventId(), log.getUserId(), log.getDetectionType(), log.getOccurredAt()));
        return CustomPageResponse.of(result.getContent(), result.getSize(), result.getNumber(),
                result.getTotalElements(), result.getTotalPages());
    }

    private Specification<TicketingDetectionLogEntity> makeSpec(TicketingDetectionLogCondRequest cond) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (cond.eventId() != null) {
                predicates.add(cb.equal(root.get("eventId"), cond.eventId()));
            }
            if (StringUtils.hasText(cond.userId())) {
                predicates.add(cb.equal(root.get("userId"), cond.userId().strip()));
            }
            if (cond.detectionType() != null) {
                predicates.add(cb.equal(root.get("detectionType"), cond.detectionType()));
            }
            if (cond.occurredFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), cond.occurredFrom()));
            }
            if (cond.occurredTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), cond.occurredTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private String serializeEvidence(Map<String, Object> evidence) {

        try {
            return objectMapper.writeValueAsString(evidence);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("티켓팅 탐지 근거를 JSON으로 변환할 수 없습니다.");
        }
    }
}
