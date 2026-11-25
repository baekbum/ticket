package dev.bum.ticket_service.config;

import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;

import java.util.Map;

/** 이벤트의 JSON 문자열과 오류 메시지의 원본 바이트를 그대로 발행한다. */
public class TicketKafkaValueSerializer extends DelegatingByTypeSerializer {

    public TicketKafkaValueSerializer() {

        super(Map.of(
                String.class, new StringSerializer(),
                byte[].class, new ByteArraySerializer()
        ));
    }
}
