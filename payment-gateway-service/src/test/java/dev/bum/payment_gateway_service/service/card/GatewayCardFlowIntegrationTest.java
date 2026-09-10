package dev.bum.payment_gateway_service.service.card;

import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveRequest;
import dev.bum.payment_gateway_service.exception.CardPaymentPendingException;
import dev.bum.payment_gateway_service.feign.ticket.TicketPaymentClient;
import dev.bum.payment_gateway_service.jpa.card.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ContextConfiguration(classes = GatewayCardFlowIntegrationTest.PersistenceConfig.class)
@Import({GatewayCardPaymentService.class, GatewayCardApprovalService.class, GatewayCardSettlementService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GatewayCardFlowIntegrationTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = DummyCard.class)
    @EnableJpaRepositories(basePackageClasses = DummyCardJpaRepository.class)
    static class PersistenceConfig { }
    @Autowired GatewayCardPaymentService payments;
    @Autowired DummyCardJpaRepository cards;
    @Autowired DummyCardPaymentHistoryJpaRepository histories;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean TicketPaymentClient ticket;
    @MockitoBean PasswordEncoder encoder;

    @BeforeEach
    void setup() {
        histories.deleteAll();
        cards.deleteAll();
        cards.save(DummyCard.builder().userId("user").cardCompany(CardCompany.SHINHAN)
                .cardNumberHash("9bbef19476623ca56c17da75fd57734dbf82530686043a6e491c6d71befe8f6e")
                .cardNumberLast4("1111").cvcHash("cvc").cardPasswordHash("pw").customerName("user")
                .issuedAt(LocalDate.now().minusYears(1)).expiresAt(LocalDate.now().plusYears(1))
                .limitAmount(BigDecimal.valueOf(100000)).build());
        when(encoder.matches(any(), any())).thenReturn(true);
        when(ticket.validateCardPayment(any())).thenReturn(payment(PaymentStatus.READY, null));
    }

    @Test
    void approvalIsCommittedBeforeTicketCompletionAndRepeatedApprovalIsIdempotent() {
        when(ticket.settleCardPayment(any())).thenAnswer(invocation -> {
            CardPaymentCompleteRequest request = invocation.getArgument(0);
            TransactionTemplate independent = new TransactionTemplate(transactionManager);
            independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            independent.executeWithoutResult(status -> {
                assertThat(histories.findByPaymentNo("PAY-1")).isPresent();
                assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("10000");
            });
            return completed(request);
        });
        String transactionId = payments.approve("user", request()).getTransactionId();
        assertThat(payments.approve("user", request()).getTransactionId()).isEqualTo(transactionId);
        assertThat(histories.findByPaymentNo("PAY-1").orElseThrow().getStatus())
                .isEqualTo(CardPaymentHistoryStatus.TICKET_PAYMENT_COMPLETED);
        verify(ticket, times(1)).validateCardPayment(any());
        verify(ticket, times(1)).settleCardPayment(any());
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void lostTicketResponsePreservesApprovalAndRetryUsesSameTransaction() {
        AtomicReference<String> transactionId = new AtomicReference<>();
        when(ticket.settleCardPayment(any())).thenAnswer(invocation -> {
            CardPaymentCompleteRequest request = invocation.getArgument(0);
            if (transactionId.get() == null) {
                transactionId.set(request.getTransactionId());
                throw new IllegalStateException("response lost after ticket commit");
            }
            assertThat(request.getTransactionId()).isEqualTo(transactionId.get());
            return completed(request);
        });
        assertThatThrownBy(() -> payments.approve("user", request())).isInstanceOf(CardPaymentPendingException.class);
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("10000");
        assertThat(payments.status("user", "PAY-1").status()).isEqualTo(CardPaymentHistoryStatus.TICKET_PAYMENT_FAILED);
        assertThat(payments.settle("PAY-1")).isEqualTo(CardPaymentHistoryStatus.TICKET_PAYMENT_COMPLETED);
        verify(ticket, never()).failCardPayment(any());
    }

    @Test
    void localCompletionCommitFailureCanBeRecoveredWithoutUndoingTicket() {
        when(ticket.settleCardPayment(any())).thenAnswer(invocation -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("commit failed"); }
            });
            return completed(invocation.getArgument(0));
        });
        assertThatThrownBy(() -> payments.approve("user", request())).isInstanceOf(CardPaymentPendingException.class);
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("10000");
        doAnswer(invocation -> completed(invocation.getArgument(0))).when(ticket).settleCardPayment(any());
        assertThat(payments.settle("PAY-1")).isEqualTo(CardPaymentHistoryStatus.TICKET_PAYMENT_COMPLETED);
        verify(ticket, never()).failCardPayment(any());
    }

    @Test
    void definiteRejectionCancelsExactlyOnce() {
        doReturn(new CardPaymentSettlementResponse(
                CardPaymentSettlementResponse.Outcome.REJECTED, payment(PaymentStatus.EXPIRED, null)))
                .when(ticket).settleCardPayment(any());
        assertThatThrownBy(() -> payments.approve("user", request())).isInstanceOf(IllegalArgumentException.class);
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("0");
        assertThat(payments.settle("PAY-1")).isEqualTo(CardPaymentHistoryStatus.CANCELLED);
        verify(ticket, times(1)).settleCardPayment(any());
    }

    @Test
    void cancellationCommitFailureKeepsApprovalForRecovery() {
        when(ticket.settleCardPayment(any())).thenAnswer(invocation -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("cancel commit failed"); }
            });
            return new CardPaymentSettlementResponse(CardPaymentSettlementResponse.Outcome.REJECTED,
                    payment(PaymentStatus.EXPIRED, null));
        });
        assertThatThrownBy(() -> payments.approve("user", request())).isInstanceOf(CardPaymentPendingException.class);
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("10000");
        doReturn(new CardPaymentSettlementResponse(
                CardPaymentSettlementResponse.Outcome.REJECTED, payment(PaymentStatus.EXPIRED, null)))
                .when(ticket).settleCardPayment(any());
        assertThat(payments.settle("PAY-1")).isEqualTo(CardPaymentHistoryStatus.CANCELLED);
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("0");
    }

    @Test
    void preflightFailureDoesNotChargeOrCreateHistory() {
        when(ticket.validateCardPayment(any())).thenThrow(new IllegalArgumentException("expired"));
        assertThatThrownBy(() -> payments.approve("user", request())).isInstanceOf(IllegalArgumentException.class);
        assertThat(histories.count()).isZero();
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("0");
        verify(ticket, never()).settleCardPayment(any());
    }

    @Test
    void approvalCommitFailureNeverCallsTicketCompletion() {
        when(ticket.validateCardPayment(any())).thenAnswer(invocation -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("approval commit failed"); }
            });
            return payment(PaymentStatus.READY, null);
        });
        assertThatThrownBy(() -> payments.approve("user", request())).isInstanceOf(IllegalStateException.class);
        assertThat(histories.count()).isZero();
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("0");
        verify(ticket, never()).settleCardPayment(any());
    }

    @Test
    void mismatchedBrowserAmountNeverCharges() {
        GatewayCardPaymentApproveRequest request = request();
        request.setAmount(BigDecimal.valueOf(9000));
        assertThatThrownBy(() -> payments.approve("user", request)).isInstanceOf(IllegalArgumentException.class);
        assertThat(histories.count()).isZero();
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("0");
        verify(ticket, never()).settleCardPayment(any());
    }

    @Test
    void stateLookupRejectsAnotherUser() {
        when(ticket.settleCardPayment(any())).thenAnswer(invocation -> completed(invocation.getArgument(0)));
        payments.approve("user", request());
        assertThatThrownBy(() -> payments.status("other", "PAY-1"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void concurrentApprovalRequestsCannotChargeTwice() throws Exception {
        when(ticket.settleCardPayment(any())).thenAnswer(invocation -> completed(invocation.getArgument(0)));
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        try (java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> attempt = () -> {
                start.await();
                try {
                    return payments.approve("user", request()).getTransactionId();
                } catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
                    return null; // 최초 요청이 동시에 들어오면 paymentNo 유일성 제약으로 한쪽의 저장이 실패할 수 있다.
                }
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            String firstId = first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            String secondId = second.get(15, java.util.concurrent.TimeUnit.SECONDS);
            String storedId = histories.findByPaymentNo("PAY-1").orElseThrow().getTransactionId();
            assertThat(firstId == null || firstId.equals(storedId)).isTrue();
            assertThat(secondId == null || secondId.equals(storedId)).isTrue();
            assertThat(firstId != null || secondId != null).isTrue();
        }
        assertThat(histories.count()).isEqualTo(1);
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("10000");
        verify(ticket, times(1)).settleCardPayment(any());
    }

    private CardPaymentSettlementResponse completed(CardPaymentCompleteRequest request) {
        return new CardPaymentSettlementResponse(CardPaymentSettlementResponse.Outcome.COMPLETED,
                payment(PaymentStatus.PAID, request.getTransactionId()));
    }

    private PaymentResponse payment(PaymentStatus status, String transactionId) {
        return PaymentResponse.builder().paymentNo("PAY-1").amount(10000).method(PaymentMethod.CREDIT_CARD)
                .status(status).cardTransactionId(transactionId).build();
    }

    private GatewayCardPaymentApproveRequest request() {
        return GatewayCardPaymentApproveRequest.builder().paymentNo("PAY-1").cardCompany(CardCompany.SHINHAN)
                .cardNumber("4111111111111111").cvc("123").cardPassword("1234").customerName("user")
                .amount(BigDecimal.valueOf(10000)).build();
    }
}
