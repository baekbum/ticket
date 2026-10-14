package dev.bum.payment_gateway_service.service.refund;

import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.dto.card.*;
import dev.bum.payment_gateway_service.dto.virtualAccount.*;
import dev.bum.payment_gateway_service.feign.ticket.TicketPaymentClient;
import dev.bum.payment_gateway_service.jpa.card.*;
import dev.bum.payment_gateway_service.jpa.refund.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import dev.bum.payment_gateway_service.service.card.GatewayCardApprovalService;
import dev.bum.payment_gateway_service.service.virtualAccount.GatewayVirtualAccountService;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@ContextConfiguration(classes = GatewayRefundIntegrationTest.PersistenceConfig.class)
@Import({GatewayCardApprovalService.class, GatewayVirtualAccountService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GatewayRefundIntegrationTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "dev.bum.payment_gateway_service.jpa")
    @EnableJpaRepositories(basePackages = "dev.bum.payment_gateway_service.jpa")
    static class PersistenceConfig { }

    @Autowired GatewayCardApprovalService cardService;
    @Autowired GatewayVirtualAccountService accountService;
    @Autowired GatewayRefundJpaRepository refunds;
    @Autowired DummyCardJpaRepository cards;
    @Autowired DummyCardPaymentHistoryJpaRepository cardHistories;
    @Autowired DummyVirtualAccountJpaRepository accounts;
    @Autowired DummyVirtualAccountPaymentHistoryJpaRepository accountHistories;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean TicketPaymentClient ticketClient;
    @MockitoBean PasswordEncoder encoder;

    @BeforeEach
    void setup() {
        refunds.deleteAll();
        cardHistories.deleteAll();
        cards.deleteAll();
        accountHistories.deleteAll();
        accounts.deleteAll();
        DummyCard card = cards.save(DummyCard.builder().userId("user").cardCompany(CardCompany.SHINHAN)
                .cardNumberHash("hash").cardNumberLast4("1111").cvcHash("cvc").cardPasswordHash("pw")
                .customerName("user").issuedAt(LocalDate.now().minusYears(1)).expiresAt(LocalDate.now().plusYears(1))
                .currentMonthUsedAmount(new BigDecimal("10000")).limitAmount(new BigDecimal("100000")).build());
        DummyCardPaymentHistory history = DummyCardPaymentHistory.approved(card, "user", "CARD-PAY", "TX-1", "****1111", new BigDecimal("10000"));
        history.completeTicketPayment(null);
        cardHistories.save(history);
        DummyVirtualAccount account = DummyVirtualAccount.issue("BANK-PAY", BankCompany.KB, "123-456", new BigDecimal("10000"), LocalDateTime.now().plusDays(1));
        account.deposit("user", LocalDateTime.now());
        account.completeTicketPayment(null);
        accounts.save(account);
    }

    @Test
    void cardReplayReturnsOriginalResultEvenAfterAnotherRefund() {
        var first = cardService.refund(cardRequest(1L, "4000"));
        cardService.refund(cardRequest(2L, "6000"));
        assertThat(cardService.refund(cardRequest(1L, "4000.00"))).isEqualTo(first);
        assertThat(first.getStatus()).isEqualTo(CardPaymentHistoryStatus.PARTIALLY_REFUNDED);
        assertThat(refunds.count()).isEqualTo(2);
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("0");
    }

    @Test
    void sameIdRejectsChangedPaymentAmountTransactionAndMethod() {
        cardService.refund(cardRequest(1L, "4000"));
        var payment = cardRequest(1L, "4000"); payment.setPaymentNo("OTHER");
        var transaction = cardRequest(1L, "4000"); transaction.setTransactionId("OTHER");
        assertThatThrownBy(() -> cardService.refund(payment)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cardService.refund(transaction)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cardService.refund(cardRequest(1L, "5000"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accountService.refund(bankRequest(1L, "4000"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(refunds.count()).isEqualTo(1);
    }

    @Test
    void primaryKeyRejectsASecondInsertInsteadOfOverwritingTheFirstRefund() {
        var first = cardService.refund(cardRequest(1L, "4000"));
        var bankRequest = bankRequest(1L, "4000");
        var bankResponse = GatewayVirtualAccountRefundResponse.builder().paymentNo("BANK-PAY")
                .refundBankCompany(BankCompany.KB).refundAccountNumber("refund-account").refundAccountHolder("user")
                .refundedAmount(new BigDecimal("4000")).message("completed").build();
        assertThatThrownBy(() -> refunds.saveAndFlush(GatewayRefund.virtualAccount(bankRequest, bankResponse)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(cardService.refund(cardRequest(1L, "4000"))).isEqualTo(first);
        assertThat(refunds.count()).isEqualTo(1);
    }

    @Test
    void missingOrNonPositiveProcessIdsAreRejectedWithoutRefunding() {
        assertThatThrownBy(() -> cardService.refund(cardRequest(null, "4000"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accountService.refund(bankRequest(0L, "4000"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(refunds.count()).isZero();
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("10000");
        assertThat(accountHistories.count()).isZero();
    }

    @Test
    void bankReplayAndAccountMismatchAndCumulativeLimit() {
        var first = accountService.refund(bankRequest(1L, "4000"));
        assertThat(accountService.refund(bankRequest(1L, "4000.00"))).isEqualTo(first);
        var changed = bankRequest(1L, "4000"); changed.setRefundAccountNumber("other");
        assertThatThrownBy(() -> accountService.refund(changed)).isInstanceOf(IllegalArgumentException.class);
        changed.setRefundAccountNumber("refund-account"); changed.setRefundAccountHolder("other");
        assertThatThrownBy(() -> accountService.refund(changed)).isInstanceOf(IllegalArgumentException.class);
        changed.setRefundAccountHolder("user"); changed.setRefundBankCompany(BankCompany.SHINHAN);
        assertThatThrownBy(() -> accountService.refund(changed)).isInstanceOf(IllegalArgumentException.class);
        accountService.refund(bankRequest(2L, "6000"));
        assertThatThrownBy(() -> accountService.refund(bankRequest(3L, "1"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(refunds.count()).isEqualTo(2);
        assertThat(accountHistories.count()).isEqualTo(2);
    }

    @Test
    void rollbackRemovesRefundRecordAndActualCardChangesAndAllowsRetry() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            cardService.refund(cardRequest(1L, "4000"));
            throw new IllegalStateException("commit failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(refunds.count()).isZero();
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("10000");
        assertThat(cardHistories.findByPaymentNo("CARD-PAY").orElseThrow().getRefundedAmount()).isEqualByComparingTo("0");
        cardService.refund(cardRequest(1L, "4000"));
        assertThat(refunds.count()).isEqualTo(1);
    }

    @Test
    void rollbackRemovesBankRefundAndPaymentHistoryTogether() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            accountService.refund(bankRequest(1L, "4000"));
            throw new IllegalStateException("commit failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(refunds.count()).isZero();
        assertThat(accountHistories.count()).isZero();
        accountService.refund(bankRequest(1L, "10000"));
        assertThat(refunds.count()).isEqualTo(1);
    }

    @Test
    void concurrentCardRequestsRefundOnlyOnce() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<GatewayCardPaymentRefundResponse> action = () -> { start.await(); return cardService.refund(cardRequest(1L, "4000")); };
            Future<GatewayCardPaymentRefundResponse> a = pool.submit(action), b = pool.submit(action);
            start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(b.get(15, TimeUnit.SECONDS));
        }
        assertThat(refunds.count()).isEqualTo(1);
        assertThat(cards.findAll().getFirst().getCurrentMonthUsedAmount()).isEqualByComparingTo("6000");
    }

    @Test
    void concurrentBankRequestsRefundOnlyOnce() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<GatewayVirtualAccountRefundResponse> action = () -> { start.await(); return accountService.refund(bankRequest(1L, "4000")); };
            Future<GatewayVirtualAccountRefundResponse> a = pool.submit(action), b = pool.submit(action);
            start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(b.get(15, TimeUnit.SECONDS));
        }
        assertThat(refunds.count()).isEqualTo(1);
        assertThat(accountHistories.count()).isEqualTo(1);
    }

    private GatewayCardPaymentRefundRequest cardRequest(Long id, String amount) {
        return GatewayCardPaymentRefundRequest.builder().paymentRefundProcessId(id).paymentNo("CARD-PAY")
                .transactionId("TX-1").refundAmount(new BigDecimal(amount)).build();
    }
    private GatewayVirtualAccountRefundRequest bankRequest(Long id, String amount) {
        return GatewayVirtualAccountRefundRequest.builder().paymentRefundProcessId(id).paymentNo("BANK-PAY")
                .refundBankCompany(BankCompany.KB).refundAccountNumber("refund-account").refundAccountHolder("user")
                .refundAmount(new BigDecimal(amount)).build();
    }
}
