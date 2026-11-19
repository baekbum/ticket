package dev.bum.payment_gateway_service.service.outbox;

import dev.bum.payment_gateway_service.dto.bank.BankTransferDelivery;
import dev.bum.payment_gateway_service.jpa.outbox.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BankTransferOutboxCommands {

    // 서로 다른 전달 단계가 공유하는 임대·재시도 저장소. 금융 상태는 변경하지 않는다.
    private final BankTransferOutboxJpaRepository events;

    /**
     * HTTP 전에 임대만 커밋하고 최초 전송 스냅샷을 반환한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BankTransferDelivery claim(Long id, BankTransferOutbox.Flow flow) {

        return events.findForUpdate(id).filter(event -> event.getFlow() == flow)
                .map(event -> event.claim(LocalDateTime.now())).orElse(null);
    }

    /**
     * 별도 도메인 변경이 없는 수신 확인만 현재 임대에 확정한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(BankTransferDelivery delivery, Map<String, Object> result) {

        events.findForUpdate(delivery.outboxId()).filter(event -> event.owns(delivery))
                .ifPresent(event -> event.delivered(result));
    }

    /**
     * 일시 실패는 같은 키·내용으로 재시도하며 늦은 이전 워커는 무시한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(BankTransferDelivery delivery, String errorType) {

        events.findForUpdate(delivery.outboxId()).filter(event -> event.owns(delivery))
                .ifPresent(event -> event.failed(errorType));
    }
}
