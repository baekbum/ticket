package dev.bum.common.service.paymentgateway.card;

import jakarta.validation.constraints.NotBlank;

public record GatewayCardSessionRequest(
        // 가맹점 서버에서 확인한 예매자. PG 원거래와 다시 대조한다.
        @NotBlank String buyerReference,

        // 로컬 취소·완료 상태에서는 조회 권한만 요청한다.
        boolean approvalAllowed
) { }
