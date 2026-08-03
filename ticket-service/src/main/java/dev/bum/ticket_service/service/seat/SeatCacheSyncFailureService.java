package dev.bum.ticket_service.service.seat;

import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailure;
import dev.bum.ticket_service.jpa.seat.cache.SeatCacheSyncFailureJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SeatCacheSyncFailureService {

    private final SeatCacheSyncFailureJpaRepository repository;

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
}
