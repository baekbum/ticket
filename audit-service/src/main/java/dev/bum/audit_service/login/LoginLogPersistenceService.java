package dev.bum.audit_service.login;

import dev.bum.common.kafka.login.LoginLogEvent;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.audit.dto.LoginLogCondRequest;
import dev.bum.common.service.audit.dto.LoginLogResponse;
import dev.bum.common.service.audit.dto.MyLoginLogResponse;
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
import java.util.Locale;
import java.util.Set;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoginLogPersistenceService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "occurredAt", "createdAt", "loginId", "result");

    private final LoginLogJpaRepository repository;

    @Transactional
    public LoginLogEntity save(LoginLogEvent event) {
        return repository.saveAndFlush(LoginLogEntity.from(event));
    }

    @Transactional(readOnly = true)
    public LoginLogResponse selectById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 로그인 로그가 존재하지 않습니다."))
                .toResponse();
    }

    @Transactional(readOnly = true)
    public CustomPageResponse<LoginLogResponse> selectByCond(LoginLogCondRequest cond) {
        LoginLogCondRequest searchCond = cond != null ? cond : new LoginLogCondRequest();
        PageRequest pageRequest = PageRequest.of(
                normalizePage(searchCond.getPage()),
                normalizeSize(searchCond.getSize()),
                makeSort(searchCond.getSort())
        );

        Page<LoginLogResponse> page = repository.findAll(makeSpec(searchCond), pageRequest)
                .map(LoginLogEntity::toResponse);

        return CustomPageResponse.of(
                page.getContent(),
                page.getSize(),
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public CustomPageResponse<MyLoginLogResponse> selectMine(String loginId, Integer page, Integer size, Integer periodDays) {
        PageRequest pageRequest = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                defaultSort()
        );
        String normalizedLoginId = loginId.toLowerCase(Locale.ROOT);
        LocalDateTime occurredFrom = LocalDateTime.now().minusDays(normalizePeriodDays(periodDays));
        Specification<LoginLogEntity> mine = (root, query, cb) -> cb.and(
                cb.equal(cb.lower(root.get("loginId")), normalizedLoginId),
                cb.greaterThanOrEqualTo(root.get("occurredAt"), occurredFrom)
        );

        Page<MyLoginLogResponse> result = repository.findAll(mine, pageRequest)
                .map(LoginLogEntity::toMyResponse);

        return CustomPageResponse.of(
                result.getContent(),
                result.getSize(),
                result.getNumber(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    private Specification<LoginLogEntity> makeSpec(LoginLogCondRequest cond) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (cond.getOccurredFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), cond.getOccurredFrom()));
            }
            if (cond.getOccurredTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), cond.getOccurredTo()));
            }
            if (cond.getAuthId() != null) {
                predicates.add(cb.equal(root.get("authId"), cond.getAuthId()));
            }
            if (StringUtils.hasText(cond.getLoginId())) {
                predicates.add(cb.like(
                        cb.lower(root.get("loginId")),
                        "%" + cond.getLoginId().toLowerCase(Locale.ROOT) + "%"
                ));
            }
            if (StringUtils.hasText(cond.getResult())) {
                predicates.add(cb.equal(root.get("result"), LoginResult.valueOf(cond.getResult().toUpperCase())));
            }
            if (StringUtils.hasText(cond.getAuthMethod())) {
                predicates.add(cb.equal(root.get("authMethod"), LoginAuthMethod.valueOf(cond.getAuthMethod().toUpperCase())));
            }
            if (StringUtils.hasText(cond.getIpAddress())) {
                predicates.add(cb.like(root.get("ipAddress"), "%" + cond.getIpAddress() + "%"));
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
            return defaultSort();
        }

        List<Sort.Order> orders = new ArrayList<>();
        for (String sort : sorts) {
            String[] parts = sort.split("-");
            if (parts.length == 2 && ALLOWED_SORT_FIELDS.contains(parts[0])) {
                try {
                    orders.add(new Sort.Order(Sort.Direction.fromString(parts[1]), parts[0]));
                } catch (IllegalArgumentException ignored) {
                    // 잘못된 정렬 방향은 무시하고 유효한 정렬 조건만 사용한다.
                }
            }
        }

        return orders.isEmpty() ? defaultSort() : Sort.by(orders);
    }

    private Sort defaultSort() {
        return Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
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

    private int normalizePeriodDays(Integer periodDays) {
        if (periodDays == null || periodDays <= 0) {
            return 30;
        }
        return Math.min(periodDays, 365);
    }
}
