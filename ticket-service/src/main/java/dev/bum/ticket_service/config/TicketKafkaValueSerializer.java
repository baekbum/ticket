package dev.bum.ticket_service.config;

import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

/** 결제 완료 JSON 문자열을 그대로 발행하고, DLT의 원본 바이트와 이벤트 객체를 모두 직렬화한다. */
public class TicketKafkaValueSerializer extends DelegatingByTypeSerializer {
    public TicketKafkaValueSerializer() {
        super(Map.of(
                String.class, new StringSerializer(),
                byte[].class, new ByteArraySerializer(),
                VirtualAccountExpiredEvent.class, new JsonSerializer<VirtualAccountExpiredEvent>().noTypeInfo()
        ));
    }
}
