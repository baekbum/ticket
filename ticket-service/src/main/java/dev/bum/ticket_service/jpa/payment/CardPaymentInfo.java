package dev.bum.ticket_service.jpa.payment;

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

    @Column(name = "card_transaction_id", length = 80)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_company", length = 30)
    private CardCompany cardCompany;

    @Column(name = "card_number_masked", length = 30)
    private String maskedCardNumber;
}
