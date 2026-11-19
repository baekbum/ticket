package dev.bum.ticket_service.jpa.payment.card;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardPaymentInfo {

    // 결제 거래 식별자.
    @Column(name = "card_transaction_id", length = 80)
    private String transactionId;

    // 카드사.
    @Enumerated(EnumType.STRING)
    @Column(name = "card_company", length = 30)
    private CardCompany cardCompany;

    // 마스킹한 카드 번호.
    @Column(name = "card_number_masked", length = 30)
    private String maskedCardNumber;
}
