package dev.bum.ticket_service.service.checkout.payment;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CheckoutPaymentService {

    private final CheckoutVirtualAccountService virtualAccountService;

    public void process(CheckoutConfirmRequest request, Payment payment) {
        if (request.getPaymentMethod() == null) {
            throw new IllegalArgumentException("지원하지 않는 결제 수단입니다.");
        }

        switch (request.getPaymentMethod()) {
            case CREDIT_CARD -> {
                // 카드 승인은 이후 승인 요청에서 처리한다.
            }
            case BANK_TRANSFER -> virtualAccountService.issue(request, payment);
            default -> throw new IllegalArgumentException("지원하지 않는 결제 수단입니다.");
        }
    }
}
