package dev.bum.ticket_service.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/** 생산자의 타입 헤더 대신 토픽별 타입 매핑으로 JSON을 변환할 이벤트 타입을 결정한다. */
public class TicketKafkaValueDeserializer extends JsonDeserializer<Object> {

    public TicketKafkaValueDeserializer(Map<String, Class<?>> eventTypes) {
        super(Object.class, false);

        Map<String, JavaType> types = new HashMap<>();
        eventTypes.forEach((topic, eventType) -> types.put(topic, TypeFactory.defaultInstance().constructType(eventType)));
        Map<String, JavaType> topicTypes = Map.copyOf(types);

        typeResolver((topic, data, headers) -> {
            JavaType type = topicTypes.get(topic);

            if (type == null) {
                throw new SerializationException("No event type registered for Kafka topic: " + topic);
            }

            return type;
        });
        // 컨슈머가 공통 spring.json.* 속성을 적용하기 전에 코드 설정을 완료하여 타입 매핑을 유지한다.
        configure(Map.of(), false);
    }
}
