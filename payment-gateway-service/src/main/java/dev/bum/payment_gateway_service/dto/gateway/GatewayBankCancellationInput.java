package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountCancelRequest;

public record GatewayBankCancellationInput(
        // 은행에 보낼 최초 취소 입력.
        BankVirtualAccountCancelRequest bankRequest,

        // 인증한 소유 가맹점.
        Long merchantId,

        // 소유 가맹점의 원결제 번호.
        String merchantPaymentNo,

        // 최초 검증된 예매 소유자.
        String buyerReference,

        // 가맹점 결과 DTO와 응답 규칙의 버전.
        String notificationContract,

        // 등록 통보 URL·인증정보를 찾는 가맹점 내부 키.
        String notificationTargetKey
) { }
