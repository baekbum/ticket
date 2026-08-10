package dev.bum.admin_service.controller.test;

import dev.bum.admin_service.config.KafkaDlqProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

@Profile("local")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/manage/test/dlt")
public class AdminDltTestController {

    private final KafkaTemplate<byte[], byte[]> kafkaTemplate;
    private final KafkaDlqProperties kafkaDlqProperties;

    @GetMapping("/topics")
    public ResponseEntity<List<String>> topics() {
        return ResponseEntity.ok(kafkaDlqProperties.getMappings().keySet().stream().sorted().toList());
    }

    @PostMapping("/publish")
    public ResponseEntity<DltTestPublishResponse> publish(@Valid @RequestBody DltTestPublishRequest request) {
        if (!kafkaDlqProperties.getMappings().containsKey(request.dltTopic())) {
            throw new IllegalArgumentException("허용되지 않은 DLT topic입니다.");
        }

        byte[] key = textBytes(request.key());
        byte[] payload = textBytes(request.payload());

        ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(request.dltTopic(), key, payload);
        record.headers().add(new RecordHeader("x-admin-test", textBytes("true")));
        record.headers().add(new RecordHeader("x-admin-test-source", textBytes("admin-service")));
        record.headers().add(new RecordHeader("x-admin-test-created-at", textBytes(OffsetDateTime.now().toString())));

        SendResult<byte[], byte[]> result = kafkaTemplate.send(record).join();

        RecordMetadata metadata = result.getRecordMetadata();
        return ResponseEntity.ok(new DltTestPublishResponse(
                metadata.topic(),
                metadata.partition(),
                metadata.offset(),
                request.key(),
                OffsetDateTime.now()
        ));
    }

    private byte[] textBytes(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
