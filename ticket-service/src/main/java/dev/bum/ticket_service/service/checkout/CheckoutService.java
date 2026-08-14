package dev.bum.ticket_service.service.checkout;

import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareRequest;
import dev.bum.common.service.ticket.checkout.dto.CheckoutPrepareResponse;
import dev.bum.ticket_service.audit.AuditLog;
import dev.bum.ticket_service.service.queue.QueueAccessService;
import dev.bum.ticket_service.service.seat.SeatCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
public class CheckoutService {

    private final SeatCacheService seatCacheService;
    private final QueueAccessService queueAccessService;

    /**
     * 좌석 선택 완료 후 배송/결제 정보 입력 화면으로 이동할 수 있는지 검증한다.
     * active token과 Redis 좌석 선점 상태가 유효하면 active token을 회수해 다음 대기자가 입장할 수 있게 한다.
     */
    @AuditLog(action = "CHECKOUT_PREPARE", targetType = "CHECKOUT")
    public CheckoutPrepareResponse prepare(String currentUserId, String activeToken, CheckoutPrepareRequest request) {
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());

        queueAccessService.validate(request.getEventId(), currentUserId, activeToken);

        seatCacheService.validateOccupiedSeat(
                request.getEventId(),
                currentUserId,
                request.getOrderId(),
                request.getSeats()
        );

        releaseActiveTokenAfterCommit(request.getEventId(), currentUserId, activeToken);

        return CheckoutPrepareResponse.builder()
                .eventId(request.getEventId())
                .orderId(request.getOrderId())
                .seats(request.getSeats())
                .idempotencyKey(idempotencyKey)
                .prepared(true)
                .preparedAt(LocalDateTime.now())
                .build();
    }

    /**
     * checkout 준비 트랜잭션이 성공한 뒤 active token을 회수해 다음 대기자가 입장할 수 있게 한다.
     */
    private void releaseActiveTokenAfterCommit(Long eventId, String userId, String activeToken) {
        runAfterCommit(() -> queueAccessService.complete(eventId, userId, activeToken));
    }

    /**
     * DB 트랜잭션 커밋이 성공한 뒤에만 외부 부수 효과를 실행한다.
     * 트랜잭션이 없을 때는 호출 위치에서 즉시 실행한다.
     */
    private void runAfterCommit(Runnable runnable) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runnable.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runnable.run();
            }
        });
    }

    /**
     * idempotencyKey는 필수로 받고, 앞뒤 공백을 제거해 저장/조회 기준을 고정한다.
     */
    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("결제 멱등 키가 필요합니다.");
        }

        return idempotencyKey.trim();
    }

}
