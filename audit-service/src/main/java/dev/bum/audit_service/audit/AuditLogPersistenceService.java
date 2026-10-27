package dev.bum.audit_service.audit;

import dev.bum.common.kafka.audit.AuditLogEvent;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.audit.dto.AuditLogCondRequest;
import dev.bum.common.service.audit.dto.AuditLogResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogPersistenceService {

    private final AuditLogJpaRepository repository;
    private final AuditLogInsertRepository insertRepository;
    private final AuditLogFingerprint fingerprint;
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    @Transactional
    public AuditLogSaveResult save(AuditLogEvent event) {
        if (event == null || !StringUtils.hasText(event.getIdempotencyKey())
                || event.getIdempotencyKey().length() > 100
                || !StringUtils.hasText(event.getServiceName()) || event.getServiceName().length() > 50
                || event.getOccurredAt() == null) {

            throw new IllegalArgumentException("감사 이벤트의 멱등키·서비스·발생 시각이 올바르지 않습니다.");
        }

        String requestHash = fingerprint.calculate(event);
        int inserted = insertRepository.insertIfAbsent(AuditLogEntity.from(event, requestHash));
        if (inserted == 1) return AuditLogSaveResult.SAVED;

        // 동시 최초 요청도 UNIQUE 검사에서 선행 트랜잭션의 종료를 기다린 뒤 이 경로로 진입한다.
        AuditLogEntity recorded = repository.findByServiceNameAndIdempotencyKey(
                event.getServiceName(), event.getIdempotencyKey()
        ).orElseThrow(() -> new IllegalStateException("기존 감사 이벤트를 찾을 수 없습니다."));

        if (!requestHash.equals(recorded.getRequestHash())) {
            throw new IllegalArgumentException("같은 서비스의 멱등키로 다른 감사 이벤트를 저장할 수 없습니다.");
        }

        return AuditLogSaveResult.DUPLICATE;
    }

    @Transactional(readOnly = true)
    public AuditLogResponse selectById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 감사 로그가 존재하지 않습니다."))
                .toResponse();
    }

    @Transactional(readOnly = true)
    public CustomPageResponse<AuditLogResponse> selectByCond(AuditLogCondRequest cond) {
        AuditLogCondRequest searchCond = cond != null ? cond : new AuditLogCondRequest();
        PageRequest pageRequest = PageRequest.of(
                normalizePage(searchCond.getPage()),
                normalizeSize(searchCond.getSize()),
                makeSort(searchCond.getSort())
        );

        Page<AuditLogResponse> page = repository.findAll(makeSpec(searchCond), pageRequest)
                .map(AuditLogEntity::toResponse);

        return CustomPageResponse.of(
                page.getContent(),
                page.getSize(),
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private Specification<AuditLogEntity> makeSpec(AuditLogCondRequest cond) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (cond.getOccurredFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), cond.getOccurredFrom()));
            }
            if (cond.getOccurredTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), cond.getOccurredTo()));
            }
            if (StringUtils.hasText(cond.getServiceName())) {
                predicates.add(cb.equal(root.get("serviceName"), cond.getServiceName()));
            }
            if (StringUtils.hasText(cond.getActorType())) {
                predicates.add(cb.equal(root.get("actorType"), cond.getActorType()));
            }
            if (StringUtils.hasText(cond.getActorId())) {
                predicates.add(cb.like(root.get("actorId"), "%" + cond.getActorId() + "%"));
            }
            if (StringUtils.hasText(cond.getActorName())) {
                predicates.add(cb.like(root.get("actorName"), "%" + cond.getActorName() + "%"));
            }
            if (StringUtils.hasText(cond.getAction())) {
                predicates.add(cb.like(root.get("action"), "%" + cond.getAction() + "%"));
            }
            if (StringUtils.hasText(cond.getTargetType())) {
                predicates.add(cb.equal(root.get("targetType"), cond.getTargetType()));
            }
            if (StringUtils.hasText(cond.getTargetId())) {
                predicates.add(cb.like(root.get("targetId"), "%" + cond.getTargetId() + "%"));
            }
            if (StringUtils.hasText(cond.getResult())) {
                predicates.add(cb.equal(root.get("result"), cond.getResult()));
            }
            if (StringUtils.hasText(cond.getRequestId())) {
                predicates.add(cb.equal(root.get("requestId"), cond.getRequestId()));
            }
            if (StringUtils.hasText(cond.getTraceId())) {
                predicates.add(cb.equal(root.get("traceId"), cond.getTraceId()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Sort makeSort(List<String> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
        }

        List<Sort.Order> orders = new ArrayList<>();
        for (String sort : sorts) {
            String[] parts = sort.split("-");
            if (parts.length == 2) {
                orders.add(new Sort.Order(Sort.Direction.fromString(parts[1]), parts[0]));
            }
        }

        return orders.isEmpty() ? Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")) : Sort.by(orders);
    }

    private int normalizePage(Integer page) {
        return page == null || page < 0 ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
