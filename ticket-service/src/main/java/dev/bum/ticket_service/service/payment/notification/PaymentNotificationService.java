package dev.bum.ticket_service.service.payment.notification;

import dev.bum.common.service.paymentgateway.notification.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentNotificationService {

    // 카드 승인 통보의 Ticksy 업무 규칙과 응답 변환.
    private final CardPaymentNotificationService cards;
    // 은행 입금 통보의 Ticksy 업무 규칙과 응답 변환.
    private final BankDepositNotificationService banks;

    /**
     * 공통 수신 API의 검증된 결제수단에 따라 로컬 처리 서비스를 선택한다.
     */
    public Object receive(GatewayPaymentNotification notification) {

        if (notification instanceof CardPaymentNotification card) {
            return cards.receive(card);
        }

        if (notification instanceof BankDepositNotification deposit) {
            return banks.receive(deposit);
        }

        throw new IllegalArgumentException("지원하지 않는 PG 통보 수단입니다.");
    }
}
