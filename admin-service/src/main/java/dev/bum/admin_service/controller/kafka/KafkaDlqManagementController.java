package dev.bum.admin_service.controller.kafka;

import dev.bum.admin_service.kafka.dlq.DlqMessageHandleRequest;
import dev.bum.admin_service.kafka.dlq.DlqMessageHandleResponse;
import dev.bum.admin_service.kafka.dlq.KafkaDlqReplayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manage/kafka-dlq")
public class KafkaDlqManagementController {

    private final KafkaDlqReplayService kafkaDlqReplayService;

    @PostMapping("/replay")
    public ResponseEntity<DlqMessageHandleResponse> replay(@Valid @RequestBody DlqMessageHandleRequest request) {
        return ResponseEntity.ok(kafkaDlqReplayService.replay(request));
    }

    @PostMapping("/discard")
    public ResponseEntity<DlqMessageHandleResponse> discard(@Valid @RequestBody DlqMessageHandleRequest request) {
        return ResponseEntity.ok(kafkaDlqReplayService.discard(request));
    }
}
