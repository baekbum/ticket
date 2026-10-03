package dev.bum.ticket_service.service.payment;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.ticket_service.feign.paymentgateway.PaymentGatewayVirtualAccountClient;
import dev.bum.ticket_service.jpa.payment.Payment;
import dev.bum.ticket_service.jpa.payment.PaymentJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VirtualAccountReconciliationServiceTest {

    @Mock private PaymentJpaRepository payments;
    @Mock private PaymentGatewayVirtualAccountClient gateway;
    @Mock private VirtualAccountReconciliationCompletionService completion;
    @Mock private VirtualAccountReconciliationValidator validator;

    @Test
    void list_uses_ticket_data_without_querying_gateway() {
        Payment payment = Payment.builder()
                .paymentNo("PAY-NEW")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.WAITING_DEPOSIT)
                .amount(180000)
                .requestedAt(LocalDateTime.of(2026, 9, 30, 12, 0))
                .build();
        given(payments.findByMethodAndStatusAndPaymentNoContainingIgnoreCase(
                eq(PaymentMethod.BANK_TRANSFER), eq(PaymentStatus.WAITING_DEPOSIT),
                eq("PAY"), any(Pageable.class)))
                .willAnswer(invocation -> new PageImpl<>(List.of(payment), invocation.getArgument(3), 1));

        var result = new VirtualAccountReconciliationService(payments, gateway, completion, validator)
                .list(0, " PAY ", PaymentStatus.WAITING_DEPOSIT);

        assertThat(result.getContent()).extracting(item -> item.getPaymentNo()).containsExactly("PAY-NEW");
        assertThat(result.getPage().getTotalElements()).isEqualTo(1);
        then(gateway).shouldHaveNoInteractions();
    }
}
