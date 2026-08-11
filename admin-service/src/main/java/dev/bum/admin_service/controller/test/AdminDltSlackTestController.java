package dev.bum.admin_service.controller.test;

import dev.bum.common.kafka.dlt.KafkaDltSlackNotifier;
import dev.bum.common.kafka.dlt.KafkaDltSlackProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@Profile("local")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manage/test/dlt/slack")
public class AdminDltSlackTestController {

    private final KafkaDltSlackNotifier kafkaDltSlackNotifier;
    private final KafkaDltSlackProperties kafkaDltSlackProperties;

    @GetMapping("/config")
    public ResponseEntity<DltSlackTestConfigResponse> config() {
        return ResponseEntity.ok(new DltSlackTestConfigResponse(
                kafkaDltSlackProperties.isEnabled(),
                StringUtils.hasText(kafkaDltSlackProperties.getWebhookUrl()),
                kafkaDltSlackProperties.getAdminDlqUrl()
        ));
    }

    @PostMapping("/send")
    public ResponseEntity<DltSlackTestResponse> send(@Valid @RequestBody DltSlackTestRequest request) {
        String skippedReason = skippedReason();
        boolean sent = false;
        if (skippedReason == null) {
            sent = kafkaDltSlackNotifier.notifyDlt(
                    new ConsumerRecord<>(
                            request.originTopic(),
                            valueOrDefault(request.originPartition(), 0),
                            valueOrDefault(request.offset(), 0L),
                            request.key(),
                            request.payload()
                    ),
                    new IllegalStateException(testExceptionMessage(request.exceptionMessage())),
                    new TopicPartition(request.dltTopic(), valueOrDefault(request.dltPartition(), 0))
            );
            if (!sent) {
                skippedReason = "Slack webhook 호출에 실패했습니다. admin-service 로그를 확인해주세요.";
            }
        }

        return ResponseEntity.ok(new DltSlackTestResponse(
                sent,
                skippedReason,
                request.originTopic(),
                request.dltTopic(),
                kafkaDltSlackProperties.getAdminDlqUrl(),
                OffsetDateTime.now()
        ));
    }

    private String skippedReason() {
        if (!kafkaDltSlackProperties.isEnabled()) {
            return "DLT Slack 알림이 비활성화되어 있습니다.";
        }
        if (!StringUtils.hasText(kafkaDltSlackProperties.getWebhookUrl())) {
            return "Slack webhook URL이 설정되어 있지 않습니다.";
        }
        return null;
    }

    private String testExceptionMessage(String exceptionMessage) {
        if (StringUtils.hasText(exceptionMessage)) {
            return "[TEST] " + exceptionMessage;
        }
        return "[TEST] 관리자 DLT Slack 알림 테스트";
    }

    private int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private long valueOrDefault(Long value, long defaultValue) {
        return value == null ? defaultValue : value;
    }
}
