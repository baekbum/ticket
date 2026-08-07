package dev.bum.admin_service.kafka.dlq;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KafkaDlqHistoryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final DlqMessageHandleHistoryJpaRepository repository;

    public Page<DlqMessageHandleHistoryResponse> histories(DlqMessageHandleHistoryCondRequest cond) {
        DlqMessageHandleHistoryCondRequest normalizedCond = cond == null ? new DlqMessageHandleHistoryCondRequest() : cond;
        Pageable pageable = PageRequest.of(
                normalizePage(normalizedCond.getPage()),
                normalizeSize(normalizedCond.getSize()),
                Sort.by(Sort.Direction.DESC, "handledAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );

        return repository.findAll(specificationOf(normalizedCond), pageable)
                .map(DlqMessageHandleHistoryResponse::new);
    }

    public DlqMessageHandleHistoryResponse history(Long id) {
        DlqMessageHandleHistory history = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DLQ 처리 이력을 찾을 수 없습니다. id=" + id));
        return new DlqMessageHandleHistoryResponse(history);
    }

    private Specification<DlqMessageHandleHistory> specificationOf(DlqMessageHandleHistoryCondRequest cond) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(cond.getDltTopic())) {
                predicates.add(criteriaBuilder.equal(root.get("dltTopic"), cond.getDltTopic()));
            }
            if (cond.getPartitionNo() != null) {
                predicates.add(criteriaBuilder.equal(root.get("partitionNo"), cond.getPartitionNo()));
            }
            if (cond.getMessageOffset() != null) {
                predicates.add(criteriaBuilder.equal(root.get("messageOffset"), cond.getMessageOffset()));
            }
            if (StringUtils.hasText(cond.getMessageKey())) {
                predicates.add(criteriaBuilder.like(root.get("messageKey"), "%" + cond.getMessageKey() + "%"));
            }
            if (StringUtils.hasText(cond.getAction())) {
                predicates.add(criteriaBuilder.equal(root.get("action"), DlqMessageHandleAction.valueOf(cond.getAction())));
            }
            if (StringUtils.hasText(cond.getStatus())) {
                predicates.add(criteriaBuilder.equal(root.get("status"), DlqMessageHandleStatus.valueOf(cond.getStatus())));
            }
            if (StringUtils.hasText(cond.getOperator())) {
                predicates.add(criteriaBuilder.like(root.get("operator"), "%" + cond.getOperator() + "%"));
            }
            if (cond.getPayloadModified() != null) {
                predicates.add(criteriaBuilder.equal(root.get("payloadModified"), cond.getPayloadModified()));
            }
            if (cond.getHandledFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("handledAt"), cond.getHandledFrom()));
            }
            if (cond.getHandledTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("handledAt"), cond.getHandledTo()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 0) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
