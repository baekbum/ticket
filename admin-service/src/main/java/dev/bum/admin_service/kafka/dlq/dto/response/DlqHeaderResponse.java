package dev.bum.admin_service.kafka.dlq.dto.response;

import lombok.Getter;

@Getter
public class DlqHeaderResponse {

    // 데이터 식별 키.
    private final String key;

    // 데이터 값.
    private final String value;

    // Base64로 인코딩한 헤더 값.
    private final String valueBase64;

    // 전달받은 값으로 처리 정보 객체를 초기화한다.
    public DlqHeaderResponse(String key, String value, String valueBase64) {

        this.key = key;
        this.value = value;
        this.valueBase64 = valueBase64;
    }
}
