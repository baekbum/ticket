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

    // 조회 요청에서 생략한 페이지 번호의 기본값.
    private static final int DEFAULT_PAGE = 0;
    // 조회 요청에서 생략한 페이지 크기의 기본값.
    private static final int DEFAULT_SIZE = 20;
    // 한 번에 조회할 수 있는 최대 항목 수.
    private static final int MAX_SIZE = 100;
    // 로그 조회에서 정렬에 사용할 수 있는 필드 목록.
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "occurredAt", "createdAt", "loginId", "result");
    // 로그인 이력 데이터를 조회하고 저장하는 레포지토리.
    private final LoginLogJpaRepository repository;

    /**
     * 로그인 이력을 저장한다.
     */
    @Transactional
    public LoginLogEntity save(LoginLogEvent event) {

        return repository.saveAndFlush(LoginLogEntity.from(event));
    }

    /**
     * 로그인 이력 정보를 식별자로 조회한다.
     */
    @Transactional(readOnly = true)
    public LoginLogResponse selectById(Long id) {

        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 로그인 로그가 존재하지 않습니다."))
                .toResponse();
    }

    /**
     * 검색 조건에 맞는 로그인 이력 정보를 조회한다.
     */
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

    /**
     * 현재 사용자의 항목을 조회한다.
     */
    @Transactional(readOnly = true)
    public CustomPageResponse<MyLoginLogResponse> selectMine(
            String loginId,
            Integer page,
            Integer size,
            Integer periodDays
    ) {

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

    /**
     * 검색 조건을 JPA 조회 조건으로 구성한다.
     */
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

    /**
     * 요청한 정렬 조건을 구성한다.
     */
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

    /**
     * 기본 조회 정렬 조건을 반환한다.
     */
    private Sort defaultSort() {

        return Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
    }

    /**
     * 페이지 번호의 기본값과 허용 범위를 적용한다.
     */
    private int normalizePage(Integer page) {

        return page == null || page < 0 ? DEFAULT_PAGE : page;
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

    /**
     * 조회 기간의 기본값과 허용 범위를 적용한다.
     */
    private int normalizePeriodDays(Integer periodDays) {

        if (periodDays == null || periodDays <= 0) {
            return 30;
        }
        return Math.min(periodDays, 365);
    }
}
