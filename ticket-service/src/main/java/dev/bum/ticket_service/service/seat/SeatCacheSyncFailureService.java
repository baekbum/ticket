package dev.bum.ticket_service.service.seat;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.SeatCacheSyncFailureCondRequest;
import dev.bum.common.service.ticket.seat.dto.SeatCacheSyncFailureHandleResponse;
import dev.bum.common.service.ticket.seat.dto.SeatCacheSyncFailureResponse;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailure;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailureJpaRepository;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailureStatus;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatCacheSyncFailureService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Duration RETRY_SEAT_CACHE_TTL = Duration.ofDays(7);

    private final SeatCacheSyncFailureJpaRepository repository;
    private final StringRedisTemplate seatRedisTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            String operation,
            String keyPrefix,
            List<String> redisKeys,
            List<String> targetValues,
            Exception exception
    ) {
        repository.save(SeatCacheSyncFailure.builder()
                .operation(operation)
                .keyPrefix(keyPrefix)
                .redisKeys(String.join("\n", redisKeys))
                .targetValue(String.join(",", targetValues))
                .failureMessage(exception.getMessage())
                .build());
    }

    @Transactional(readOnly = true)
    public CustomPageResponse<SeatCacheSyncFailureResponse> selectByCond(SeatCacheSyncFailureCondRequest cond) {
        SeatCacheSyncFailureCondRequest normalizedCond = cond == null ? new SeatCacheSyncFailureCondRequest() : cond;
        Pageable pageable = PageRequest.of(
                normalizePage(normalizedCond.getPage()),
                normalizeSize(normalizedCond.getSize()),
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))
        );
        Page<SeatCacheSyncFailure> page = repository.findAll(specificationOf(normalizedCond), pageable);
        List<SeatCacheSyncFailureResponse> content = page.getContent()
                .stream()
                .map(this::responseOf)
                .collect(Collectors.toList());

        return CustomPageResponse.of(content, page.getSize(), page.getNumber(), page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SeatCacheSyncFailureResponse selectById(Long id) {
        return responseOf(findById(id));
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public SeatCacheSyncFailureHandleResponse retry(Long id) {
        SeatCacheSyncFailure failure = findById(id);
        if (failure.getStatus() != SeatCacheSyncFailureStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 Redis 보정 이력만 재처리할 수 있습니다. id=" + id);
        }

        try {
            List<String> redisKeys = parseLines(failure.getRedisKeys());
            List<String> targetValues = parseValues(failure.getTargetValue());
            validateRetryTarget(redisKeys, targetValues);

            for (int index = 0; index < redisKeys.size(); index++) {
                String redisKey = redisKeys.get(index);
                String targetValue = targetValues.size() == 1 ? targetValues.get(0) : targetValues.get(index);
                seatRedisTemplate.opsForValue().set(redisKey, targetValue, RETRY_SEAT_CACHE_TTL);
                seatRedisTemplate.delete(redisKey + ":lock");
            }

            failure.resolve("Redis 좌석 캐시 보정 재처리 완료");
            return new SeatCacheSyncFailureHandleResponse(failure.getId(), failure.getStatus().name(), failure.getRetryCount(), failure.getResolvedMessage());
        } catch (RuntimeException e) {
            failure.failRetry(e.getMessage());
            throw e;
        }
    }

    @Transactional
    public SeatCacheSyncFailureHandleResponse discard(Long id) {
        SeatCacheSyncFailure failure = findById(id);
        if (failure.getStatus() != SeatCacheSyncFailureStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 Redis 보정 이력만 폐기할 수 있습니다. id=" + id);
        }

        failure.discard("운영자 판단으로 Redis 보정 대상에서 제외");
        return new SeatCacheSyncFailureHandleResponse(failure.getId(), failure.getStatus().name(), failure.getRetryCount(), failure.getResolvedMessage());
    }

    private SeatCacheSyncFailure findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Redis 보정 이력을 찾을 수 없습니다. id=" + id));
    }

    private Specification<SeatCacheSyncFailure> specificationOf(SeatCacheSyncFailureCondRequest cond) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(cond.getOperation())) {
                predicates.add(criteriaBuilder.equal(root.get("operation"), cond.getOperation()));
            }
            if (StringUtils.hasText(cond.getKeyPrefix())) {
                predicates.add(criteriaBuilder.equal(root.get("keyPrefix"), cond.getKeyPrefix()));
            }
            if (StringUtils.hasText(cond.getStatus())) {
                predicates.add(criteriaBuilder.equal(root.get("status"), SeatCacheSyncFailureStatus.valueOf(cond.getStatus())));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private SeatCacheSyncFailureResponse responseOf(SeatCacheSyncFailure failure) {
        SeatCacheSyncFailureResponse response = new SeatCacheSyncFailureResponse();
        response.setId(failure.getId());
        response.setOperation(failure.getOperation());
        response.setKeyPrefix(failure.getKeyPrefix());
        response.setRedisKeys(failure.getRedisKeys());
        response.setTargetValue(failure.getTargetValue());
        response.setFailureMessage(failure.getFailureMessage());
        response.setStatus(failure.getStatus().name());
        response.setRetryCount(failure.getRetryCount());
        response.setCreatedAt(failure.getCreatedAt());
        response.setLastFailedAt(failure.getLastFailedAt());
        response.setResolvedAt(failure.getResolvedAt());
        response.setResolvedMessage(failure.getResolvedMessage());
        return response;
    }

    private List<String> parseLines(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private List<String> parseValues(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private void validateRetryTarget(List<String> redisKeys, List<String> targetValues) {
        if (redisKeys.isEmpty()) {
            throw new IllegalStateException("재처리할 Redis key가 없습니다.");
        }
        if (targetValues.isEmpty()) {
            throw new IllegalStateException("재처리할 Redis target value가 없습니다.");
        }
        if (targetValues.size() != 1 && targetValues.size() != redisKeys.size()) {
            throw new IllegalStateException("Redis key 수와 target value 수가 일치하지 않습니다.");
        }
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
