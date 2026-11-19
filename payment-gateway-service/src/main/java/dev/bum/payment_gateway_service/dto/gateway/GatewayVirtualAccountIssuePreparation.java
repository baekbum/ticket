package dev.bum.payment_gateway_service.dto.gateway;

import dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountIssueRequest;

public record GatewayVirtualAccountIssuePreparation(
        // PG 트랜잭션 밖으로 전달할 불변 은행 입력. 엔티티나 잠금을 전달하지 않는다.
        BankVirtualAccountIssueRequest bankRequest,

        // 이미 성공한 발급은 저장한 결과를 바로 재응답한다. 최초 요청에는 null이다.
        GatewayVirtualAccountPaymentIssueResponse replay,

        // 현재 신규 금융 실행이 가능한 가맹점인지 나타낸다. 기존 금융 성공의 복구는 별도로 허용한다.
        boolean activeMerchant
) { }
