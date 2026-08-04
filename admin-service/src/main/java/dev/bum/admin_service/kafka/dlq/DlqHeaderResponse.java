package dev.bum.admin_service.kafka.dlq;

import lombok.Getter;

@Getter
public class DlqHeaderResponse {

    private final String key;
    private final String value;
    private final String valueBase64;

    public DlqHeaderResponse(String key, String value, String valueBase64) {
        this.key = key;
        this.value = value;
        this.valueBase64 = valueBase64;
    }
}
