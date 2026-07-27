package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.dto.CardPaymentApproveRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class MockCardAuthorizationService {

    /**
     * 실제 PG/카드사 승인 연동을 대신하는 토이 프로젝트용 카드 승인 시뮬레이터.
     * 요청 카드 정보가 아래 fixture 중 하나와 일치하면 승인 성공으로 간주한다.
     */
    private static final List<MockCard> MOCK_CARDS = List.of(
            new MockCard("KB", "1234567890123456", "123", "qwe123!"),
            new MockCard("SHINHAN", "5555444433332222", "456", "asd123!"),
            new MockCard("HYUNDAI", "4111111111111111", "789", "zxc123!")
    );

    public boolean approve(CardPaymentApproveRequest request) {
        String cardCompany = normalizeCompany(request.getCardCompany());
        String cardNumber = normalizeCardNumber(request.getCardNumber());
        String cvc = normalizeSimpleValue(request.getCvc());
        String cardPassword = normalizeSimpleValue(request.getCardPassword());

        return MOCK_CARDS.stream()
                .anyMatch(card -> card.matches(cardCompany, cardNumber, cvc, cardPassword));
    }

    private String normalizeCompany(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeCardNumber(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("[^0-9]", "") : "";
    }

    private String normalizeSimpleValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private static class MockCard {

        private final String cardCompany;
        private final String cardNumber;
        private final String cvc;
        private final String cardPassword;

        private MockCard(String cardCompany, String cardNumber, String cvc, String cardPassword) {
            this.cardCompany = cardCompany;
            this.cardNumber = cardNumber;
            this.cvc = cvc;
            this.cardPassword = cardPassword;
        }

        private boolean matches(String cardCompany, String cardNumber, String cvc, String cardPassword) {
            return this.cardCompany.equals(cardCompany)
                    && this.cardNumber.equals(cardNumber)
                    && this.cvc.equals(cvc)
                    && this.cardPassword.equals(cardPassword);
        }
    }
}
