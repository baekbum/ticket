package dev.bum.admin_service.kafka.dlq.support;

import dev.bum.admin_service.kafka.dlq.dto.response.DlqHeaderResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.header.Headers;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DlqRecordCodec {
    private final ObjectMapper objectMapper;

    // 같은 이름의 헤더와 null 값, 원본 바이트를 순서대로 보존한다.
    public String encode(Headers headers) {
        List<StoredHeader> values = new ArrayList<>();
        headers.forEach(header -> values.add(new StoredHeader(header.key(), header.value())));
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("DLT 헤더를 저장할 수 없습니다.", e);
        }
    }

    public List<StoredHeader> headers(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("저장된 DLT 헤더를 읽을 수 없습니다.", e);
        }
    }

    public List<DlqHeaderResponse> responses(String json) {
        return headers(json).stream().map(h -> new DlqHeaderResponse(h.key(), decode(h.value()), base64(h.value()))).toList();
    }

    public static String decode(byte[] value) {
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    public static String base64(byte[] value) {
        return value == null ? null : Base64.getEncoder().encodeToString(value);
    }

    public static String historyText(byte[] value) {
        String text = decode(value);
        // PostgreSQL text에는 NUL 문자를 넣을 수 없다. 원본은 Inbox의 bytea에 그대로 보관한다.
        return text == null ? null : text.replace("\u0000", "\\u0000");
    }

    public record StoredHeader(String key, byte[] value) {}
}
