package dev.bum.ticket_service.service.checkout.payment.virtualAccount;

import dev.bum.common.service.ticket.checkout.dto.CheckoutConfirmRequest;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.ticket_service.jpa.payment.payment.Payment;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CheckoutVirtualAccountService {

    // 공연일과 입금기한을 한국 시간으로 계산하는 애플리케이션 시계.
    private final Clock clock;

    /**
     * 로컬 트랜잭션에 최초 발급 의도를 저장한다. PG HTTP 호출은 커밋 후 수행한다.
     */
    public void issue(CheckoutConfirmRequest request, Payment payment) {

        BankCompany bankCompany = resolveBankCompany(request);

        if (payment.getGatewayBankCompany() != null) {
            if (payment.getGatewayBankCompany() != bankCompany) {
                throw new IllegalArgumentException("같은 결제번호의 최초 은행과 일치하지 않습니다.");
            }

            return;
        }

        payment.planGatewayAccount(bankCompany, calculateDeadline(payment));
    }

    // 은행 도메인에 공연일을 보내지 않고 Ticksy에서 일반 입금기한으로 계산한다.
    public LocalDateTime calculateDeadline(Payment payment) {

        LocalDate today = LocalDate.now(clock);
        LocalDate eventDate = payment.getReservation().getEvent().getEventDateTime().toLocalDate();

        if (!today.isBefore(eventDate)) {
            throw new IllegalArgumentException("공연 당일에는 무통장 입금을 사용할 수 없습니다.");
        }

        LocalDate expirationDate = today.plusDays(1).isBefore(eventDate) ? today.plusDays(1) : today;
        return LocalDateTime.of(expirationDate, LocalTime.of(23, 59, 59));
    }

    /**
     * 최초 발급 요청의 은행 코드를 확인한다.
     */
    public BankCompany resolveBankCompany(CheckoutConfirmRequest request) {

        if (!StringUtils.hasText(request.getBankCode())) {
            throw new IllegalArgumentException("은행 코드가 필요합니다.");
        }

        try {
            return BankCompany.valueOf(request.getBankCode().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("지원하지 않는 은행 코드입니다.");
        }
    }
}
