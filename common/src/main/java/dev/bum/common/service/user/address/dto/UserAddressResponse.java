package dev.bum.common.service.user.address.dto;

import dev.bum.common.service.user.address.enums.AddressStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserAddressResponse {

    // 배송지 식별자.
    private Long addressId;

    // 사용자 내부 식별자.
    private Long userPk;

    // 사용자 ID.
    private String userId;

    // 표시용 별칭.
    private String alias;

    // 수령인 이름.
    private String recipientName;

    // 수령인 연락처.
    private String recipientPhone;

    // 우편번호.
    private String zipCode;

    // 주소.
    private String address;

    // 상세 주소.
    private String detailAddress;

    // 기본 배송지 여부.
    private Boolean defaultAddress;

    // 사용자 배송지 상태.
    private AddressStatus status;

    // 생성 시각.
    private LocalDateTime createdAt;

    // 마지막 수정 시각.
    private LocalDateTime updatedAt;
}
