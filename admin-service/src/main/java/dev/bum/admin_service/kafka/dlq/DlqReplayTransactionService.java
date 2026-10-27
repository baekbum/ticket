package dev.bum.admin_service.kafka.dlq;

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
    private final DlqMessageJpaRepository messages;
    private final DlqMessageHandleHistoryJpaRepository histories;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DlqReplayAttempt begin(DlqMessageHandleRequest request, String targetTopic,
                                  DlqMessageHandleAction action, String modifiedPayload) {
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(DlqMessageHandleRequest request, Long historyId, DlqMessageHandleStatus result, String errorType) {
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

    private DlqMessage lock(DlqMessageHandleRequest request) {
        return messages.findForUpdate(request.getDltTopic(), request.getPartition(), request.getOffset())
                .orElseThrow(() -> new IllegalArgumentException("DB에 수집된 DLT 메시지를 찾을 수 없습니다."));
    }

    private String historyKey(byte[] key) {
        String text = DlqRecordCodec.historyText(key);
        // 기존 이력의 키는 미리보기이며, 재발행에는 Inbox의 원본 바이트를 사용한다.
        return text == null || text.length() <= 500 ? text : text.substring(0, 500);
    }
}
