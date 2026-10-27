package dev.bum.audit_service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.bum.common.kafka.audit.AuditLogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

@Component
@RequiredArgsConstructor
public class AuditLogFingerprint {
    private final ObjectMapper objectMapper;

    public String calculate(AuditLogEvent event) {
        try {
            JsonNode payload = canonical(objectMapper.valueToTree(event));
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException | IllegalArgumentException e) {
            // 감사 데이터 원문이 예외나 로그에 포함되지 않도록 오류 유형만 전달한다.
            throw new IllegalArgumentException("감사 이벤트 내용을 검증할 수 없습니다.");
        }
    }

    private JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            // 중첩 객체도 키 순서를 맞춰 DLT 재발행 시 JSON 필드 순서에 영향을 받지 않는다.
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            value.fields().forEachRemaining(field -> fields.put(field.getKey(), canonical(field.getValue())));
            ObjectNode result = objectMapper.createObjectNode();
            fields.forEach(result::set);
            return result;
        }

        if (value.isArray()) {
            // 배열의 순서는 내용의 일부이므로 유지한다.
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(element -> result.add(canonical(element)));
            return result;
        }

        if (value.isNumber()) return DecimalNode.valueOf(value.decimalValue().stripTrailingZeros());

        return value;
    }
}
