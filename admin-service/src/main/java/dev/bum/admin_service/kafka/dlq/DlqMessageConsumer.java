package dev.bum.admin_service.kafka.dlq;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DlqMessageConsumer {
    private final DlqMessageIngestionService ingestionService;

    @KafkaListener(topics = "#{@kafkaDlqProperties.getMappings().keySet().toArray(new String[0])}",
            groupId = "${app.kafka-dlq.inbox-group-id:admin-dlq-inbox-v1}",
            containerFactory = "kafkaDlqInboxListenerFactory")
    public void consume(ConsumerRecord<byte[], byte[]> record) {
        // 프록시 호출이 반환되면 DB 커밋이 끝난 상태다. 그 후 컨테이너가 오프셋을 커밋한다.
        ingestionService.save(record);
    }
}
