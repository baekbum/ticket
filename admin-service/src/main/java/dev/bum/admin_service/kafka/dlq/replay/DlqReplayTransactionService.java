package dev.bum.admin_service.kafka.dlq.replay;

import dev.bum.admin_service.kafka.dlq.dto.request.DlqMessageHandleRequest;
import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleAction;
import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleHistory;
import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleHistoryJpaRepository;
import dev.bum.admin_service.kafka.dlq.history.DlqMessageHandleStatus;
import dev.bum.admin_service.kafka.dlq.message.DlqMessage;
import dev.bum.admin_service.kafka.dlq.message.DlqMessageJpaRepository;
import dev.bum.admin_service.kafka.dlq.message.DlqMessageStatus;
import dev.bum.admin_service.kafka.dlq.support.DlqRecordCodec;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DlqReplayTransactionService {

    // 실패 메시지 데이터를 조회하고 저장하는 레포지토리.
    private final DlqMessageJpaRepository messages;
    // 처리 이력 목록.
    private final DlqMessageHandleHistoryJpaRepository histories;

    /**
     * 중단된 처리 항목을 재시도할 수 있도록 해제한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseStalled(DlqMessageHandleRequest request) {

        DlqMessage message = lock(request);
        if (message.getStatus() != DlqMessageStatus.REPLAYING
                || message.getUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(10))) {
            throw new IllegalStateException("10분 이상 남아 있는 재발행 건만 확인 불가 상태로 전환할 수 있습니다.");
        }
        histories.findById(message.getCurrentHistoryId()).orElseThrow()
                .complete(DlqMessageHandleStatus.UNKNOWN, "관리자 확인: " + request.getOperator() + " / " + request.getReason());
        message.changeStatus(DlqMessageStatus.UNKNOWN);
    }

    /**
     * 처리 정보 처리에서 처리를 시작하고 진행 상태를 기록한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DlqReplayAttempt begin(
            DlqMessageHandleRequest request,
            String targetTopic,
            DlqMessageHandleAction action,
            String modifiedPayload
    ) {

        DlqMessage message = lock(request);
        DlqMessageHandleHistory history = histories.save(DlqMessageHandleHistory.builder()
                .dltTopic(request.getDltTopic()).partitionNo(request.getPartition()).messageOffset(request.getOffset())
                .messageKey(historyKey(message.getMessageKey())).targetTopic(targetTopic)
                .action(action).status(DlqMessageHandleStatus.PROCESSING)
                .operator(request.getOperator()).reason(request.getReason())
                .originalPayload(DlqRecordCodec.historyText(message.getPayload())).modifiedPayload(modifiedPayload)
                .payloadModified(action == DlqMessageHandleAction.MODIFIED_REPLAY).build());
        // 같은 행의 잠금 아래 처리권과 이력을 함께 커밋한다. 이후 네트워크 대기 중에는 DB 잠금을 유지하지 않는다.
        message.start(history.getId());
        if (action == DlqMessageHandleAction.DISCARD) {
            message.changeStatus(DlqMessageStatus.DISCARDED);
            history.complete(DlqMessageHandleStatus.SUCCESS, null);
        }
        return DlqReplayAttempt.builder().historyId(history.getId()).targetTopic(targetTopic)
                .key(message.getMessageKey()).headersJson(message.getHeadersJson())
                .payload(modifiedPayload == null ? message.getPayload() : modifiedPayload.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    /**
     * 처리 정보 처리에서 처리 결과와 완료 상태를 기록한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(
            DlqMessageHandleRequest request,
            Long historyId,
            DlqMessageHandleStatus result,
            String errorType
    ) {

        DlqMessage message = lock(request);
        // 관리자가 복구한 뒤 늦게 도착한 이전 호출 결과가 새 시도를 덮어쓰지 못하게 한다.
        if (!Objects.equals(message.getCurrentHistoryId(), historyId) || message.getStatus() != DlqMessageStatus.REPLAYING) {
            throw new IllegalStateException("현재 진행 중인 재발행 시도가 아닙니다.");
        }
        DlqMessageHandleHistory history = histories.findById(historyId).orElseThrow();
        history.complete(result, errorType);
        message.changeStatus(switch (result) {
            case SUCCESS -> history.getAction() == DlqMessageHandleAction.MODIFIED_REPLAY
                    ? DlqMessageStatus.MODIFIED_REPLAYED : DlqMessageStatus.REPLAYED;
            case FAILED -> DlqMessageStatus.FAILED;
            case UNKNOWN -> DlqMessageStatus.UNKNOWN;
            default -> throw new IllegalArgumentException("완료 상태가 아닙니다.");
        });
    }

    /**
     * 처리 정보 처리에서 동시 변경을 막기 위한 잠금을 획득한다.
     */
    private DlqMessage lock(DlqMessageHandleRequest request) {

        return messages.findForUpdate(request.getDltTopic(), request.getPartition(), request.getOffset())
                .orElseThrow(() -> new IllegalArgumentException("DB에 수집된 DLT 메시지를 찾을 수 없습니다."));
    }

    /**
     * 처리 이력을 식별할 키를 구성한다.
     */
    private String historyKey(byte[] key) {

        String text = DlqRecordCodec.historyText(key);
        // 기존 이력의 키는 미리보기이며, 재발행에는 Inbox의 원본 바이트를 사용한다.
        return text == null || text.length() <= 500 ? text : text.substring(0, 500);
    }
}
