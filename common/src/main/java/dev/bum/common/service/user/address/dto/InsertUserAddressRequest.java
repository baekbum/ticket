package dev.bum.common.service.user.address.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InsertUserAddressRequest {

    // 사용자 ID.
    private String userId;

    // 표시용 별칭.
    private String alias;

    // 수령인 이름.
    @NotBlank
    private String recipientName;

    // 수령인 연락처.
    @NotBlank
    private String recipientPhone;

    // 우편번호.
    @NotBlank
    private String zipCode;

    // 주소.
    @NotBlank
    private String address;

    // 상세 주소.
    private String detailAddress;

    // 기본 배송지 여부.
    private Boolean defaultAddress;
}
