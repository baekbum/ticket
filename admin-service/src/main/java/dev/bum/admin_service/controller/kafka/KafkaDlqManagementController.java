package dev.bum.admin_service.controller.kafka;

import dev.bum.admin_service.kafka.dlq.dto.request.DlqMessageHandleHistoryCondRequest;
import dev.bum.admin_service.kafka.dlq.dto.request.DlqMessageHandleRequest;
import dev.bum.admin_service.kafka.dlq.dto.request.DlqMessageModifiedReplayRequest;
import dev.bum.admin_service.kafka.dlq.dto.response.DlqMessageDetailResponse;
import dev.bum.admin_service.kafka.dlq.dto.response.DlqMessageHandleHistoryResponse;
import dev.bum.admin_service.kafka.dlq.dto.response.DlqMessageHandleResponse;
import dev.bum.admin_service.kafka.dlq.dto.response.DlqMessageSummaryResponse;
import dev.bum.admin_service.kafka.dlq.dto.response.DlqTopicResponse;
import dev.bum.admin_service.kafka.dlq.history.KafkaDlqHistoryService;
import dev.bum.admin_service.kafka.dlq.message.DlqMessageStatus;
import dev.bum.admin_service.kafka.dlq.message.KafkaDlqQueryService;
import dev.bum.admin_service.kafka.dlq.replay.KafkaDlqReplayService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manage/kafka-dlq")
public class KafkaDlqManagementController {

    private final KafkaDlqQueryService kafkaDlqQueryService;
    private final KafkaDlqReplayService kafkaDlqReplayService;
    private final KafkaDlqHistoryService kafkaDlqHistoryService;

    @GetMapping("/topics")
    public ResponseEntity<List<DlqTopicResponse>> topics() {
        return ResponseEntity.ok(kafkaDlqQueryService.topics());
    }

    @GetMapping("/messages")
    public ResponseEntity<List<DlqMessageSummaryResponse>> messages(
            @RequestParam("dltTopic") String dltTopic,
            @RequestParam(value = "partition", required = false) Integer partition,
            @RequestParam(value = "fromOffset", required = false) Long fromOffset,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "status", required = false) DlqMessageStatus status
    ) {
        return ResponseEntity.ok(kafkaDlqQueryService.messages(dltTopic, partition, fromOffset, size, page, status));
    }

    @GetMapping("/messages/detail")
    public ResponseEntity<DlqMessageDetailResponse> detail(
            @RequestParam("dltTopic") String dltTopic,
            @RequestParam("partition") int partition,
            @RequestParam("offset") long offset
    ) {
        return ResponseEntity.ok(kafkaDlqQueryService.detail(dltTopic, partition, offset));
    }

    @PostMapping("/replay")
    public ResponseEntity<DlqMessageHandleResponse> replay(@Valid @RequestBody DlqMessageHandleRequest request) {
        return ResponseEntity.ok(kafkaDlqReplayService.replay(request));
    }

    @PostMapping("/replay/modified")
    public ResponseEntity<DlqMessageHandleResponse> replayModified(@Valid @RequestBody DlqMessageModifiedReplayRequest request) {
        return ResponseEntity.ok(kafkaDlqReplayService.replayModified(request));
    }

    @PostMapping("/discard")
    public ResponseEntity<DlqMessageHandleResponse> discard(@Valid @RequestBody DlqMessageHandleRequest request) {
        return ResponseEntity.ok(kafkaDlqReplayService.discard(request));
    }

    @PostMapping("/replay/release-stalled")
    public ResponseEntity<Void> releaseStalled(@Valid @RequestBody DlqMessageHandleRequest request) {
        kafkaDlqReplayService.releaseStalled(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/histories")
    public ResponseEntity<Page<DlqMessageHandleHistoryResponse>> histories(@RequestBody(required = false) DlqMessageHandleHistoryCondRequest cond) {
        return ResponseEntity.ok(kafkaDlqHistoryService.histories(cond));
    }

    @GetMapping("/histories/{id}")
    public ResponseEntity<DlqMessageHandleHistoryResponse> history(@PathVariable("id") Long id) {
        return ResponseEntity.ok(kafkaDlqHistoryService.history(id));
    }
}
