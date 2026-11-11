package dev.bum.ticket_service.service.payment.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.service.paymentgateway.notification.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.jpa.payment.payment.*;
import dev.bum.ticket_service.jpa.payment.virtualAccount.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class GatewayBankCancellationNotificationService {

    // 예매 취소 때 이미 처리한 Payment와 소유자·계좌를 검증한다. 좌석·티켓을 다시 변경하지 않는다.
    private final PaymentJpaRepository payments;
    // 수신 완료·거절 결과를 모두 저장하고 같은 이벤트의 다른 내용은 거절한다.
    private final GatewayBankCancellationReceiptJpaRepository receipts;
    // 최초 통보의 JSON 스냅샷 변환.
    private final ObjectMapper json;
    // 이 수신 서버에 등록된 PG 가맹점 코드.
    private final long merchantId;

    /**
     * 로컬 결제 저장소와 서버 가맹점 설정을 연결한다.
     */
    public GatewayBankCancellationNotificationService(
            PaymentJpaRepository payments,
            GatewayBankCancellationReceiptJpaRepository receipts,
            ObjectMapper json,
            @Value("${app.payment-gateway.merchant-id:1}") long merchantId
    ) {

        if (merchantId <= 0) {
            throw new IllegalArgumentException("PG 가맹점 코드 설정이 필요합니다.");
        }

        this.payments = payments;
        this.receipts = receipts;
        this.json = json;
        this.merchantId = merchantId;
    }

    /**
     * PG의 확정 취소 결과를 저장한 뒤 수신 확인한다. 은행 거절은 예매 취소를 되돌리지 않고 이력에 보존한다.
     */
    @Transactional
    public BankCancellationNotificationResponse receive(BankCancellationNotification event) {

        event.validateContract();

        if (!Long.valueOf(merchantId).equals(event.merchantId())) {
            throw new AccessDeniedException("이 가맹점에 대한 PG 통보가 아닙니다.");
        }

        Payment payment = payments.findByPaymentNoForUpdate(event.merchantPaymentNo())
                .orElseThrow(() -> new IllegalArgumentException("통보 대상 결제가 없습니다."));

        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER || payment.getReservation() == null
                || !event.buyerReference().equals(payment.getReservation().getUserId())
                || BigDecimal.valueOf(payment.getAmount()).compareTo(event.amount()) != 0
                || !event.accountNumber().equals(payment.getAccountNumber())
                || !event.bankCompany().getBankName().equals(payment.getBankName())) {
            throw new IllegalArgumentException("취소 결과 통보가 로컬 원거래와 일치하지 않습니다.");
        }

        var previous = receipts.findByPaymentNo(event.merchantPaymentNo());

        if (previous.isPresent()) {
            BankCancellationNotification original = json.convertValue(previous.get().getPayload(), BankCancellationNotification.class);

            if (!original.equals(event)) {
                throw new IllegalArgumentException("같은 취소 이벤트의 최초 내용과 일치하지 않습니다.");
            }

            return BankCancellationNotificationResponse.of(original);
        }

        if ("CANCELLED".equals(event.outcome()) && payment.getStatus() != PaymentStatus.CANCELLED) {
            throw new IllegalStateException("로컬 예매 취소가 확인되지 않았습니다.");
        }

        receipts.saveAndFlush(GatewayBankCancellationReceipt.received(event.eventId(), event.merchantPaymentNo(),
                json.convertValue(event, new TypeReference<>() { })));

        return BankCancellationNotificationResponse.of(event);
    }
}
