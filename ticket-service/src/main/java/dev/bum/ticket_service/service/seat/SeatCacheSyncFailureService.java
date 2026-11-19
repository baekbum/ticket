package dev.bum.ticket_service.service.seat;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.ticket.seat.dto.SeatCacheSyncFailureCondRequest;
import dev.bum.common.service.ticket.seat.dto.SeatCacheSyncFailureHandleResponse;
import dev.bum.common.service.ticket.seat.dto.SeatCacheSyncFailureResponse;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailure;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailureJpaRepository;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailureStatus;
import dev.bum.ticket_service.jpa.seat.Seat;
import dev.bum.ticket_service.jpa.seat.SeatJpaRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
@RequiredArgsConstructor
public class SeatCacheSyncFailureService {

    // 조회 요청에서 생략한 페이지 번호의 기본값.
    private static final int DEFAULT_PAGE = 0;
    // 조회 요청에서 생략한 페이지 크기의 기본값.
    private static final int DEFAULT_SIZE = 20;
    // 한 번에 조회할 수 있는 최대 항목 수.
    private static final int MAX_SIZE = 100;
    // 좌석 캐시 동기화 재시도에 적용할 유효 시간.
    private static final Duration RETRY_SEAT_CACHE_TTL = Duration.ofDays(7);
    // 좌석 Redis 키에서 식별 정보를 추출하는 패턴.
    private static final Pattern SEAT_KEY_PATTERN = Pattern.compile("^event:(\\d+):seat:(.+):(\\d+):(\\d+)$");
    // 소유 정보를 갖지 않는 과거 이력으로 현재 사용자 선점을 해제하지 않는다.
    private static final DefaultRedisScript<Long> RETRY_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('get', KEYS[1])
            if redis.call('exists', KEYS[2]) == 1
                or (current and string.sub(current, 1, 7) == 'LOCKED:') then
                return 0
            end
            redis.call('psetex', KEYS[1], ARGV[1], ARGV[2])
            return 1
            """, Long.class);
    // 좌석 캐시 동기화 실패 데이터를 조회하고 저장하는 레포지토리.
    private final SeatCacheSyncFailureJpaRepository repository;
    // 문자열 키와 값을 저장하고 조회하는 Redis 클라이언트.
    private final StringRedisTemplate seatRedisTemplate;
    // 좌석 데이터를 조회하고 저장하는 레포지토리.
    private final SeatJpaRepository seatRepository;

    /**
     * 실패 사유와 재시도 정보를 기록한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            String operation,
            String keyPrefix,
            List<String> redisKeys,
            List<String> targetValues,
            Exception exception
    ) {

        validateRetryTarget(redisKeys, targetValues);
        for (int index = 0; index < redisKeys.size(); index++) {
            repository.save(SeatCacheSyncFailure.builder()
                .operation(operation)
                .keyPrefix(keyPrefix)
                .redisKeys(redisKeys.get(index))
                .targetValue(targetValues.size() == 1 ? targetValues.get(0) : targetValues.get(index))
                .failureMessage(exception.getMessage())
                .build());
        }
    }

    /**
     * 검색 조건에 맞는 좌석 캐시 동기화 실패 정보 정보를 조회한다.
     */
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

    /**
     * 좌석 캐시 동기화 실패 정보 정보를 식별자로 조회한다.
     */
    @Transactional(readOnly = true)
    public SeatCacheSyncFailureResponse selectById(Long id) {

        return responseOf(findById(id));
    }

    /**
     * 실패한 동기화 작업을 재시도한다.
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public SeatCacheSyncFailureHandleResponse retry(Long id) {

        SeatCacheSyncFailure failure = findByIdForUpdate(id);
        if (failure.getStatus() != SeatCacheSyncFailureStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 Redis 보정 이력만 재처리할 수 있습니다. id=" + id);
        }

        try {
            List<String> redisKeys = parseLines(failure.getRedisKeys());
            List<String> targetValues = parseValues(failure.getTargetValue());
            validateRetryTarget(redisKeys, targetValues);

            for (int index = 0; index < redisKeys.size(); index++) {
                String redisKey = redisKeys.get(index);
                Seat seat = findSeatForUpdate(redisKey);
                Long result = seatRedisTemplate.execute(RETRY_SCRIPT,
                        List.of(redisKey, redisKey + ":lock"),
                        String.valueOf(retryTtl(seat).toMillis()), seat.getStatus().name());
                if (!Long.valueOf(1L).equals(result)) {
                    throw new IllegalStateException("현재 좌석 선점이 있거나 Redis 보정 결과를 확인할 수 없습니다. key=" + redisKey);
                }
            }

            failure.resolve("현재 DB 상태 기준 Redis 좌석 캐시 보정 완료");
            return new SeatCacheSyncFailureHandleResponse(failure.getId(), failure.getStatus().name(), failure.getRetryCount(), failure.getResolvedMessage());
        } catch (RuntimeException e) {
            failure.failRetry(e.getMessage());
            throw e;
        }
    }

    /**
     * 실패 메시지를 폐기 상태로 변경한다.
     */
    @Transactional
    public SeatCacheSyncFailureHandleResponse discard(Long id) {

        SeatCacheSyncFailure failure = findByIdForUpdate(id);
        if (failure.getStatus() != SeatCacheSyncFailureStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 Redis 보정 이력만 폐기할 수 있습니다. id=" + id);
        }

        failure.discard("운영자 판단으로 Redis 보정 대상에서 제외");
        return new SeatCacheSyncFailureHandleResponse(failure.getId(), failure.getStatus().name(), failure.getRetryCount(), failure.getResolvedMessage());
    }

    /**
     * 좌석 캐시 동기화 실패 정보 정보를 식별자로 조회한다.
     */
    private SeatCacheSyncFailure findById(Long id) {

        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Redis 보정 이력을 찾을 수 없습니다. id=" + id));
    }

    /**
     * 좌석 캐시 동기화 실패 정보 정보를 변경 잠금과 함께 식별자로 조회한다.
     */
    private SeatCacheSyncFailure findByIdForUpdate(Long id) {

        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Redis 보정 이력을 찾을 수 없습니다. id=" + id));
    }

    /**
     * 좌석 정보를 변경 잠금과 함께 조회한다.
     */
    private Seat findSeatForUpdate(String redisKey) {

        Matcher matcher = SEAT_KEY_PATTERN.matcher(redisKey);
        if (!matcher.matches()) {
            throw new IllegalStateException("좌석 Redis key 형식이 올바르지 않습니다. key=" + redisKey);
        }
        List<Seat> seats = seatRepository.findByCacheCoordinatesForUpdate(
                Long.valueOf(matcher.group(1)), matcher.group(2),
                Integer.valueOf(matcher.group(3)), Integer.valueOf(matcher.group(4)));
        if (seats.size() != 1) {
            throw new IllegalStateException("보정 대상 좌석을 하나로 식별할 수 없습니다. key=" + redisKey);
        }
        return seats.get(0);
    }

    /**
     * 동기화 재시도에 사용할 유효 시간을 계산한다.
     */
    private Duration retryTtl(Seat seat) {

        if (seat.getEvent() == null || seat.getEvent().getEventDateTime() == null) {
            return RETRY_SEAT_CACHE_TTL;
        }
        int runningMinutes = seat.getEvent().getRunningMinutes() == null ? 0 : seat.getEvent().getRunningMinutes();
        Duration ttl = Duration.between(LocalDateTime.now(), seat.getEvent().getEventDateTime().plusMinutes(runningMinutes));
        return ttl.isPositive() ? ttl : Duration.ofMinutes(1);
    }

    /**
     * 검색 요청을 JPA 조회 조건으로 변환한다.
     */
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

    /**
     * 처리 정보를 응답 객체로 구성한다.
     */
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

    /**
     * 스크립트 결과를 줄 단위 항목으로 분리한다.
     */
    private List<String> parseLines(String value) {

        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    /**
     * 스크립트 결과의 값을 파싱한다.
     */
    private List<String> parseValues(String value) {

        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    /**
     * 재시도 대상과 현재 상태가 유효한지 검증한다.
     */
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
