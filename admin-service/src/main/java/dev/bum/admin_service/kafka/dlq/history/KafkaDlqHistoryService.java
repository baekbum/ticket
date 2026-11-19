package dev.bum.admin_service.kafka.dlq.history;

import dev.bum.admin_service.kafka.dlq.dto.request.DlqMessageHandleHistoryCondRequest;
import dev.bum.admin_service.kafka.dlq.dto.response.DlqMessageHandleHistoryResponse;

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

    // 조회 요청에서 생략한 페이지 번호의 기본값.
    private static final int DEFAULT_PAGE = 0;
    // 조회 요청에서 생략한 페이지 크기의 기본값.
    private static final int DEFAULT_SIZE = 20;
    // 한 번에 조회할 수 있는 최대 항목 수.
    private static final int MAX_SIZE = 100;
    // 실패 메시지 처리 이력 데이터를 조회하고 저장하는 레포지토리.
    private final DlqMessageHandleHistoryJpaRepository repository;

    /**
     * 처리 이력을 조회한다.
     */
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

    /**
     * 단일 처리 이력을 조회한다.
     */
    public DlqMessageHandleHistoryResponse history(Long id) {

        DlqMessageHandleHistory history = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DLQ 처리 이력을 찾을 수 없습니다. id=" + id));
        return new DlqMessageHandleHistoryResponse(history);
    }

    /**
     * 검색 요청을 JPA 조회 조건으로 변환한다.
     */
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

    /**
     * 페이지 번호의 기본값과 허용 범위를 적용한다.
     */
    private int normalizePage(Integer page) {

        if (page == null || page < 0) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    /**
     * 페이지 크기의 기본값과 허용 범위를 적용한다.
     */
    private int normalizeSize(Integer size) {

        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
