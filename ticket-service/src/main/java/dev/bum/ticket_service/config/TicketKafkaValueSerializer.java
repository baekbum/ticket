package dev.bum.ticket_service.config;

import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;

import java.util.Map;

/** 결제 완료 JSON 문자열을 그대로 발행하고, 오류 메시지의 원본 바이트를 직렬화한다. */
public class TicketKafkaValueSerializer extends DelegatingByTypeSerializer {

    public TicketKafkaValueSerializer() {

        super(Map.of(
                String.class, new StringSerializer(),
                byte[].class, new ByteArraySerializer()
        ));
    }
}
