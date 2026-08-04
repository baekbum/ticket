package dev.bum.admin_service.controller.kafka;

import dev.bum.admin_service.kafka.dlq.DlqMessageHandleRequest;
import dev.bum.admin_service.kafka.dlq.DlqMessageHandleResponse;
import dev.bum.admin_service.kafka.dlq.DlqMessageDetailResponse;
import dev.bum.admin_service.kafka.dlq.DlqMessageSummaryResponse;
import dev.bum.admin_service.kafka.dlq.DlqTopicResponse;
import dev.bum.admin_service.kafka.dlq.KafkaDlqQueryService;
import dev.bum.admin_service.kafka.dlq.KafkaDlqReplayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/topics")
    public ResponseEntity<List<DlqTopicResponse>> topics() {
        return ResponseEntity.ok(kafkaDlqQueryService.topics());
    }

    @GetMapping("/messages")
    public ResponseEntity<List<DlqMessageSummaryResponse>> messages(
            @RequestParam("dltTopic") String dltTopic,
            @RequestParam(value = "partition", required = false) Integer partition,
            @RequestParam(value = "fromOffset", required = false) Long fromOffset,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        return ResponseEntity.ok(kafkaDlqQueryService.messages(dltTopic, partition, fromOffset, size));
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

    @PostMapping("/discard")
    public ResponseEntity<DlqMessageHandleResponse> discard(@Valid @RequestBody DlqMessageHandleRequest request) {
        return ResponseEntity.ok(kafkaDlqReplayService.discard(request));
    }
}
