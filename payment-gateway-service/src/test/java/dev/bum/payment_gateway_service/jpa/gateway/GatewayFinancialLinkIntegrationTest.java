package dev.bum.payment_gateway_service.jpa.gateway;

import dev.bum.payment_gateway_service.security.merchant.MerchantCredentialKey;

import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.jpa.card.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never"})
@ContextConfiguration(classes = GatewayFinancialLinkIntegrationTest.Config.class)
class GatewayFinancialLinkIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "dev.bum.payment_gateway_service.jpa")
    @EnableJpaRepositories(basePackages = "dev.bum.payment_gateway_service.jpa")
    static class Config { }

    @Autowired TestEntityManager entityManager;
    @Autowired MerchantJpaRepository merchants;
    @Autowired GatewayPaymentJpaRepository payments;
    @Autowired GatewayPaymentHistoryJpaRepository histories;
    @Autowired DummyCardJpaRepository cards;
    @Autowired DummyCardPaymentHistoryJpaRepository approvals;
    @Autowired DummyVirtualAccountJpaRepository accounts;
    @Autowired DummyVirtualAccountPaymentHistoryJpaRepository bankHistories;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void financialRowsAndBankHistoryRetainTheirGatewayLinksAfterReload() {
        Merchant merchant = merchant();
        GatewayPayment cardPayment = payment(merchant, "LINK-CARD", PaymentMethod.CREDIT_CARD);
        GatewayPayment bankPayment = payment(merchant, "LINK-BANK", PaymentMethod.BANK_TRANSFER);
        approvals.saveAndFlush(approval(cardPayment, card(), "LINK-CARD"));
        DummyVirtualAccount account = accounts.saveAndFlush(account(bankPayment, "LINK-BANK", "1000-1000"));
        Long bankHistoryId = bankHistories.saveAndFlush(DummyVirtualAccountPaymentHistory.issued(account)).getHistoryId();
        entityManager.clear();

        assertThat(approvals.findByPgPaymentId(cardPayment.getPgPaymentId()).orElseThrow().getPgPaymentId())
                .isEqualTo(cardPayment.getPgPaymentId());
        assertThat(accounts.findByPgPaymentId(bankPayment.getPgPaymentId()).orElseThrow().getAccountNumber())
                .isEqualTo("1000-1000");
        assertThat(accounts.findPgPaymentIdByAccountNumber("1000-1000")).contains(bankPayment.getPgPaymentId());
        assertThat(bankHistories.findById(bankHistoryId).orElseThrow().getVirtualAccount().getPgPaymentId())
                .isEqualTo(bankPayment.getPgPaymentId());
        assertThat(payments.findById(cardPayment.getPgPaymentId()).orElseThrow().getStatus())
                .isEqualTo(GatewayPaymentStatus.READY);
    }

    @Test
    void oneCardLedgerSupportsSeveralGatewayPayments() {
        Merchant merchant = merchant();
        DummyCard card = card();
        GatewayPayment first = payment(merchant, "CARD-FIRST", PaymentMethod.CREDIT_CARD);
        GatewayPayment second = payment(merchant, "CARD-SECOND", PaymentMethod.CREDIT_CARD);
        approvals.saveAndFlush(approval(first, card, "CARD-FIRST"));
        approvals.saveAndFlush(approval(second, card, "CARD-SECOND"));
        entityManager.clear();

        assertThat(approvals.findByPgPaymentId(first.getPgPaymentId()).orElseThrow().getDummyCard().getDummyCardId())
                .isEqualTo(card.getDummyCardId());
        assertThat(approvals.findByPgPaymentId(second.getPgPaymentId()).orElseThrow().getDummyCard().getDummyCardId())
                .isEqualTo(card.getDummyCardId());
        assertThat(cards.count()).isEqualTo(1);
        assertThat(approvals.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"CREDIT_CARD", "BANK_TRANSFER"})
    void theSameGatewayPaymentCannotHaveTwoRowsInTheSameFinancialDomain(PaymentMethod method) {
        GatewayPayment payment = payment(merchant(), "DUPLICATE-LINK", method);
        if (method == PaymentMethod.CREDIT_CARD) {
            DummyCard card = card();
            approvals.saveAndFlush(approval(payment, card, "FIRST-NO"));
            assertThatThrownBy(() -> approvals.saveAndFlush(approval(payment, card, "SECOND-NO")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } else {
            accounts.saveAndFlush(account(payment, "FIRST-NO", "2000-1000"));
            assertThatThrownBy(() -> accounts.saveAndFlush(account(payment, "SECOND-NO", "2000-2000")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void failedApprovalWithoutACardCanStillReferToTheGatewayPayment() {
        GatewayPayment payment = payment(merchant(), "FAILED-CARD", PaymentMethod.CREDIT_CARD);
        approvals.saveAndFlush(DummyCardPaymentHistory.approvalFailed(payment.getPgPaymentId(), null, "buyer",
                "FAILED-CARD", CardCompany.SHINHAN, "4111-****-****-1111", new BigDecimal("100.00"), "카드 없음"));
        entityManager.clear();

        DummyCardPaymentHistory failed = approvals.findByPgPaymentId(payment.getPgPaymentId()).orElseThrow();
        assertThat(failed.getDummyCard()).isNull();
        assertThat(failed.getStatus()).isEqualTo(CardPaymentHistoryStatus.APPROVAL_FAILED);
    }

    @Test
    void linkedFactoriesRequireAnExplicitGatewayIdentifier() {
        assertThatThrownBy(() -> DummyCardPaymentHistory.approved(null, null, "buyer", "NO", "TX",
                "4111-****-****-1111", new BigDecimal("100.00"))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DummyCardPaymentHistory.approvalFailed(null, null, "buyer", "NO",
                CardCompany.SHINHAN, "4111-****-****-1111", new BigDecimal("100.00"), "실패"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DummyVirtualAccount.issue(null, "NO", BankCompany.KB, "4000-1000",
                new BigDecimal("100.00"), LocalDateTime.now().plusDays(1))).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"CREDIT_CARD", "BANK_TRANSFER"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aHistoryFailureRollsBackTheNewGatewayAndFinancialRowsTogether(PaymentMethod method) {
        AtomicReference<String> id = new AtomicReference<>();
        String paymentNo = "ROLLBACK-" + UUID.randomUUID();

        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            GatewayPayment payment = payment(merchant(), paymentNo, method);
            id.set(payment.getPgPaymentId());
            if (method == PaymentMethod.CREDIT_CARD) {
                approvals.saveAndFlush(approval(payment, card(), paymentNo));
            } else {
                accounts.saveAndFlush(account(payment, paymentNo, "5000-1000"));
            }
            histories.saveAndFlush(GatewayPaymentHistory.registered(payment));
            histories.saveAndFlush(GatewayPaymentHistory.registered(payment));
        })).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(id.get()).isNotNull();
        assertThat(payments.findById(id.get())).isEmpty();
        assertThat(approvals.findByPgPaymentId(id.get())).isEmpty();
        assertThat(accounts.findByPgPaymentId(id.get())).isEmpty();
        assertThat(histories.countByPaymentPgPaymentId(id.get())).isZero();
    }

    Merchant merchant() {
        Merchant merchant = Merchant.register("연결 검증 가맹점", MerchantCredentialKey.hash("link-" + UUID.randomUUID()), "TICKSY", "verify");
        merchant.activate();
        return merchants.saveAndFlush(merchant);
    }

    GatewayPayment payment(Merchant merchant, String paymentNo, PaymentMethod method) {
        GatewayPaymentTerms terms = new GatewayPaymentTerms(paymentNo, "buyer", method, new BigDecimal("100.00"),
                LocalDateTime.of(2099, 1, 1, 23, 59, 59), method == PaymentMethod.BANK_TRANSFER ? BankCompany.KB : null);
        return payments.saveAndFlush(GatewayPayment.prepare(merchant, terms));
    }

    DummyCard card() {
        return cards.saveAndFlush(DummyCard.builder().userId("card-owner").cardCompany(CardCompany.SHINHAN)
                .cardNumberHash(UUID.randomUUID().toString()).cardNumberLast4("1111")
                .cvcHash("test-cvc-hash").cardPasswordHash("test-password-hash").customerName("카드 소유자")
                .issuedAt(LocalDate.of(2020, 1, 1)).expiresAt(LocalDate.of(2099, 1, 1))
                .limitAmount(new BigDecimal("10000.00")).build());
    }

    DummyCardPaymentHistory approval(GatewayPayment payment, DummyCard card, String paymentNo) {
        return DummyCardPaymentHistory.approved(payment.getPgPaymentId(), card, "buyer", paymentNo,
                "TX-" + UUID.randomUUID(), "4111-****-****-1111", payment.getAmount());
    }

    DummyVirtualAccount account(GatewayPayment payment, String paymentNo, String accountNumber) {
        return DummyVirtualAccount.issue(payment.getPgPaymentId(), paymentNo, BankCompany.KB, accountNumber,
                payment.getAmount(), payment.getExpiresAt());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = PaymentMethod.class, names = {"CREDIT_CARD", "BANK_TRANSFER"})
    void everyFinancialRowRequiresAGatewayPayment(PaymentMethod method) {
        GatewayPayment payment = payment(merchant(), "NO-NULL-LINK", method);

        if (method == PaymentMethod.CREDIT_CARD) {
            DummyCardPaymentHistory row = approval(payment, card(), payment.getPgPaymentId());
            org.springframework.test.util.ReflectionTestUtils.setField(row, "pgPaymentId", null);
            assertThatThrownBy(() -> approvals.saveAndFlush(row)).isInstanceOf(DataIntegrityViolationException.class);
        } else {
            DummyVirtualAccount row = account(payment, payment.getPgPaymentId(), "REQ-1000");
            org.springframework.test.util.ReflectionTestUtils.setField(row, "pgPaymentId", null);
            assertThatThrownBy(() -> accounts.saveAndFlush(row)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
