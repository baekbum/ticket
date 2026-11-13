package dev.bum.payment_gateway_service.jpa.gateway;

import dev.bum.payment_gateway_service.security.merchant.MerchantCredentialKey;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.payment_gateway_service.jpa.card.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** 실제 DB에서는 DDL을 검증만 하고 각 테스트의 데이터 변경은 전부 롤백한다. */
@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=never"})
@ContextConfiguration(classes = GatewayCorePostgresTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "GATEWAY_CORE_VERIFY_URL", matches = "jdbc:postgresql:.*")
class GatewayCorePostgresTest {
    @Autowired dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutboxJpaRepository transferEvents;
    @Autowired dev.bum.payment_gateway_service.jpa.virtualAccount.BankVirtualAccountCancellationJpaRepository cancelReceipts;

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Merchant.class, GatewayPayment.class, DummyCard.class, DummyVirtualAccount.class, dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox.class})
    @EnableJpaRepositories(basePackageClasses = {MerchantJpaRepository.class, GatewayPaymentJpaRepository.class,
            DummyCardJpaRepository.class, DummyVirtualAccountJpaRepository.class,
            dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutboxJpaRepository.class})
    static class Config { }

    @DynamicPropertySource
    static void connection(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("GATEWAY_CORE_VERIFY_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("GATEWAY_CORE_VERIFY_USER"));
        properties.add("spring.datasource.password", () -> System.getenv("GATEWAY_CORE_VERIFY_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired MerchantJpaRepository merchants;
    @Autowired GatewayPaymentJpaRepository payments;
    @Autowired GatewayPaymentHistoryJpaRepository histories;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestEntityManager entityManager;
    @Autowired DummyCardJpaRepository cards;
    @Autowired DummyCardPaymentHistoryJpaRepository approvals;
    @Autowired DummyVirtualAccountJpaRepository accounts;
    @Autowired DummyVirtualAccountPaymentHistoryJpaRepository bankHistories;
    @Autowired GatewayCardNotificationJpaRepository notifications;
    @Autowired MerchantNotificationEndpointJpaRepository endpoints;
    @Autowired GatewayBankNotificationJpaRepository bankNotifications;
    @Test
    void aMerchantCanBeUnprovisionedAndLaterStoreOnlyTheIssuedHash() {
        Merchant merchant = merchants.saveAndFlush(Merchant.register("미발급 가맹점", null, "TICKSY", "verify"));
        String key = MerchantCredentialKey.generate("ticksy");
        merchant.changeCredentialHash(MerchantCredentialKey.hash(key));
        merchants.flush();
        entityManager.clear();
        assertThat(merchants.findById(merchant.getMerchantId()).orElseThrow().getCredentialHash())
                .isEqualTo(MerchantCredentialKey.hash(key)).doesNotContain("ticksy");
    }

    @Test
    void twoMerchantsCannotShareTheSameCredentialHashOnPostgres() {
        String hash = MerchantCredentialKey.hash(MerchantCredentialKey.generate("ticksy"));
        merchants.saveAndFlush(Merchant.register("중복 이름", hash, "TICKSY", "verify"));
        assertThatThrownBy(() -> merchants.saveAndFlush(Merchant.register("중복 이름", hash, "TICKSY", "verify")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bankExpirationFlowAndPgHistoryAreAcceptedByTheActualPostgresSchema() {
        GatewayPayment payment = payment(merchant(), "VERIFY-BANK-EXPIRATION", PaymentMethod.BANK_TRANSFER);
        var event = new dev.bum.payment_gateway_service.dto.bank.BankExpirationEvent(
                payment.getPgPaymentId() + ":EXPIRE:INITIAL", payment.getPgPaymentId(), "1111-123456",
                payment.getBankCompany(), payment.getAmount(), payment.getExpiresAt(), payment.getExpiresAt().plusSeconds(1));
        var outbox = transferEvents.saveAndFlush(dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox.pending(
                dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox.Flow.BANK_EXPIRATION,
                event.eventId(), event.pgPaymentId(), java.util.Map.of("eventId", event.eventId(), "expiredAt", event.expiredAt().toString())));
        GatewayPaymentStatus before = payment.getStatus();
        payment.expireVirtualAccount();
        histories.saveAndFlush(GatewayPaymentHistory.virtualAccountExpired(payment, event, before));
        entityManager.clear();
        assertThat(transferEvents.findById(outbox.getOutboxId()).orElseThrow().getFlow())
                .isEqualTo(dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox.Flow.BANK_EXPIRATION);
        assertThat(payments.findById(payment.getPgPaymentId()).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.EXPIRED);
        assertThat(histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(payment.getPgPaymentId(),
                GatewayPaymentOperation.EXPIRE, "INITIAL")).isPresent();
    }

    @Test
    void cancellationOutboxStepsHaveIndependentKeysLeasesAndResultsOnPostgres() {
        GatewayPayment payment = payment(merchant(), "VERIFY-OUTBOX-STEPS", PaymentMethod.BANK_TRANSFER);
        var request = transferEvents.saveAndFlush(dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox.pending(
                dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox.Flow.BANK_CANCEL_REQUEST,
                payment.getPgPaymentId() + ":CANCEL:INITIAL", payment.getPgPaymentId(), java.util.Map.of("amount", "100.00")));
        var notice = transferEvents.saveAndFlush(dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox.pending(
                dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox.Flow.MERCHANT_CANCEL_RESULT,
                payment.getPgPaymentId() + ":CANCEL:INITIAL", payment.getPgPaymentId(), java.util.Map.of("outcome", "REJECTED")));
        var delivery = request.claim(LocalDateTime.now());
        transferEvents.flush();
        request.delivered(java.util.Map.of("outcome", "REJECTED", "reason", "ALREADY_DEPOSITED"));
        transferEvents.flush();
        entityManager.clear();
        var stored = transferEvents.findById(request.getOutboxId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo("DELIVERED");
        assertThat(stored.getResult()).containsEntry("outcome", "REJECTED");
        assertThat(stored.owns(delivery)).isFalse();
        assertThat(transferEvents.findById(notice.getOutboxId()).orElseThrow().getStatus()).isEqualTo("PENDING");
        assertThat(transferEvents.findReadyIds(notice.getFlow(), LocalDateTime.now(),
                org.springframework.data.domain.PageRequest.of(0, 100))).contains(notice.getOutboxId());
    }

    @Test
    void aDurableBankCancellationReceiptPreservesNullableJsonResultFieldsOnPostgres() {
        GatewayPayment payment = payment(merchant(), "VERIFY-BANK-RECEIPT", PaymentMethod.BANK_TRANSFER);
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("outcome", "REJECTED");
        result.put("account", null);
        cancelReceipts.saveAndFlush(dev.bum.payment_gateway_service.jpa.virtualAccount.BankVirtualAccountCancellation.completed(
                payment.getPgPaymentId(), java.util.Map.of("amount", "100.00", "accountNumber", "not-issued"), result));
        entityManager.clear();
        assertThat(cancelReceipts.findById(payment.getPgPaymentId()).orElseThrow().getResultPayload())
                .containsEntry("outcome", "REJECTED").containsEntry("account", null);
    }

    @Test
    void cancellationSnapshotUsesTheActualPostgresSchema() {
        GatewayPayment payment = payment(merchant(), "VERIFY-CANCELLATION", PaymentMethod.BANK_TRANSFER);
        DummyVirtualAccount account = accounts.saveAndFlush(DummyVirtualAccount.issue(payment.getPgPaymentId(),
                payment.getPgPaymentId(), BankCompany.KB, "VERIFY-CANCEL-ACCOUNT", payment.getAmount(), payment.getExpiresAt()));
        payment.waitForDeposit();
        account.cancel(dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountCancelResponse.REQUESTED_REASON);
        bankHistories.saveAndFlush(DummyVirtualAccountPaymentHistory.cancelledByGateway(account));
        payment.cancelVirtualAccount();
        LocalDateTime cancelledAt = payment.getCancelledAt();
        GatewayPaymentHistory history = histories.saveAndFlush(GatewayPaymentHistory.virtualAccountCancelled(payment,
                dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountCancelResponse.from(account),
                GatewayPaymentStatus.WAITING_DEPOSIT));
        entityManager.clear();
        assertThat(histories.findById(history.getHistoryId()).orElseThrow().getDetails())
                .containsEntry("accountNumber", "VERIFY-CANCEL-ACCOUNT").containsEntry("fundsReturned", false);
        assertThat(payments.findById(payment.getPgPaymentId()).orElseThrow().getCancelledAt()).isEqualTo(cancelledAt);
    }

    @Test
    void cancellingAnExistingDepositPreservesItsTimestamp() {
        GatewayPayment payment = payment(merchant(), "VERIFY-CANCEL-DEPOSIT", PaymentMethod.BANK_TRANSFER);
        DummyVirtualAccount account = accounts.saveAndFlush(DummyVirtualAccount.issue(payment.getPgPaymentId(),
                payment.getPgPaymentId(), BankCompany.KB, "VERIFY-CANCEL-DEPOSIT", payment.getAmount(), payment.getExpiresAt()));
        LocalDateTime depositedAt = LocalDateTime.of(2026, 11, 10, 12, 0, 0, 123456000);
        account.deposit("입금자", depositedAt);
        account.cancel("MERCHANT_RESERVATION_CANCELLED_NO_FUNDS_RETURNED");
        accounts.flush();
        payment.waitForDeposit();
        payment.receiveDeposit(depositedAt);
        payment.cancelDepositedPayment();
        GatewayPaymentHistory history = histories.saveAndFlush(GatewayPaymentHistory.virtualAccountCancelled(payment,
                dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountCancelResponse.from(account), GatewayPaymentStatus.PAID));
        entityManager.clear();
        assertThat(payments.findById(payment.getPgPaymentId()).orElseThrow().getPaidAt()).isEqualTo(depositedAt);
        assertThat(accounts.findByPgPaymentId(payment.getPgPaymentId()).orElseThrow().getDepositedAt()).isEqualTo(depositedAt);
        assertThat(histories.findById(history.getHistoryId()).orElseThrow().getDetails())
                .containsEntry("depositedAt", depositedAt.toString()).containsEntry("fundsReturned", false);
    }

    @Test
    void bankDepositOutboxAndImmutableFactsAreMappedOnTheActualPostgresSchema() {
        Merchant merchant = merchant();
        GatewayPayment payment = payment(merchant, "VERIFY-BANK-DEPOSIT", PaymentMethod.BANK_TRANSFER);
        payment.waitForDeposit();
        LocalDateTime at = LocalDateTime.of(2026, 11, 9, 12, 0, 0, 123456000);
        DummyVirtualAccount account = accounts.saveAndFlush(DummyVirtualAccount.issue(payment.getPgPaymentId(),
                payment.getPgPaymentId(), BankCompany.KB, "VERIFY-BANK-ACCOUNT", payment.getAmount(), payment.getExpiresAt()));
        account.deposit("입금자", at);
        bankHistories.saveAndFlush(DummyVirtualAccountPaymentHistory.deposited(account));
        assertThat(accounts.findUnnotifiedDepositIds(org.springframework.data.domain.PageRequest.of(0, 100)))
                .contains(payment.getPgPaymentId());
        var bank = new dev.bum.payment_gateway_service.dto.bank.BankVirtualAccountDepositResponse(payment.getPgPaymentId(),
                BankCompany.KB, BankCompany.KB.getBankName(), "VERIFY-BANK-ACCOUNT", payment.getAmount(),
                payment.getExpiresAt(), "입금자", at, VirtualAccountPaymentStatus.DEPOSITED);
        payment.receiveDeposit(at);
        histories.saveAndFlush(GatewayPaymentHistory.deposited(payment, bank, GatewayPaymentStatus.WAITING_DEPOSIT));
        Long id = bankNotifications.saveAndFlush(GatewayBankNotification.pending(payment, bank)).getNotificationId();
        transferEvents.saveAndFlush(dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox.pending(
                dev.bum.payment_gateway_service.jpa.outbox.BankTransferOutbox.Flow.BANK_DEPOSIT,
                payment.getPgPaymentId() + ":DEPOSIT:INITIAL", payment.getPgPaymentId(), java.util.Map.of("verified", true)));
        assertThat(accounts.findUnnotifiedDepositIds(org.springframework.data.domain.PageRequest.of(0, 100)))
                .doesNotContain(payment.getPgPaymentId());
        entityManager.clear();
        GatewayBankNotification event = bankNotifications.findForUpdate(id).orElseThrow();
        assertThat(event.claim(LocalDateTime.now()).depositedAt()).isEqualTo(at);
        bankNotifications.flush();
        event.delivered("APPLIED");
        bankNotifications.flush();
        entityManager.clear();
        assertThat(bankNotifications.findById(id).orElseThrow().getStatus()).isEqualTo("DELIVERED");
        assertThat(payments.findById(payment.getPgPaymentId()).orElseThrow().getPaidAt()).isEqualTo(at);
    }

    @Test
    void actualBankNotificationCompositeForeignKeyRejectsAnotherMerchant() {
        GatewayPayment payment = payment(merchant(), "VERIFY-BANK-NOTIFY-OWNER", PaymentMethod.BANK_TRANSFER);
        Merchant other = merchant();
        assertThatThrownBy(() -> jdbc.update("""
                insert into gateway_bank_notification_outbox(event_id, pg_payment_id, merchant_id,
                    merchant_payment_no, buyer_reference, notification_contract, notification_target_key,
                    account_number, bank_company, depositor_name, amount, deposited_at)
                values ('VERIFY-BANK-EVENT', ?, ?, 'VERIFY-NO', 'verify', 'PAYMENT_NOTIFICATION_V1', 'verify',
                    'VERIFY-ACCOUNT', 'KB', '입금자', 100, CURRENT_TIMESTAMP)
                """, payment.getPgPaymentId(), other.getMerchantId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void notificationEndpointKeysAreMerchantScopedAndPersistEncryptedCredentials() {
        Merchant first = merchant();
        Merchant second = merchant();
        dev.bum.payment_gateway_service.service.gateway.notification.MerchantNotificationTokenCipher cipher =
                new dev.bum.payment_gateway_service.service.gateway.notification.MerchantNotificationTokenCipher("verify-key");
        MerchantNotificationEndpoint a = endpoints.saveAndFlush(MerchantNotificationEndpoint.register(
                first, "same-key", "https://first.example/hook", cipher.encrypt("first-secret")));
        endpoints.saveAndFlush(MerchantNotificationEndpoint.register(
                second, "same-key", "https://second.example/hook", cipher.encrypt("second-secret")));
        entityManager.clear();
        assertThat(cipher.decrypt(endpoints.findByMerchantMerchantIdAndTargetKey(first.getMerchantId(), "same-key")
                .orElseThrow().getServiceTokenCiphertext())).isEqualTo("first-secret");
        assertThat(endpoints.findByMerchantMerchantIdAndTargetKey(second.getMerchantId(), "same-key")
                .orElseThrow().getEndpointId()).isNotEqualTo(a.getEndpointId());
        assertThat(endpoints.findByMerchantMerchantIdAndTargetKey(second.getMerchantId(), "missing")).isEmpty();
        assertThat(jdbc.queryForObject("select service_token_ciphertext from merchant_notification_endpoints "
                + "where endpoint_id = ?", String.class, a.getEndpointId())).doesNotContain("first-secret");
    }

    @Test
    void actualEndpointConstraintRejectsDuplicateKeysForTheSameMerchant() {
        Merchant owner = merchant();
        endpoints.saveAndFlush(MerchantNotificationEndpoint.register(owner, "unique-key", "https://first.example/hook",
                "v1:verify-encrypted-value"));
        assertThatThrownBy(() -> endpoints.saveAndFlush(MerchantNotificationEndpoint.register(owner, "unique-key",
                "https://second.example/hook", "v1:another-encrypted-value")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void migratedSchemaAndNonSecretTicksyRegistrationExist() {
        assertThat(merchants.findById(1L)).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conrelid = 'gateway_payments'::regclass "
                + "and conname in ('uk_gateway_payments_merchant_no', 'fk_gateway_payments_merchant', "
                + "'chk_gateway_payments_amount')", Long.class)).isEqualTo(3);
    }

    @Test
    void sameNamesAndNumbersAreScopedAndJsonHistoryIsMapped() {
        Merchant first = merchant();
        Merchant second = merchant();
        GatewayPayment a = payments.saveAndFlush(GatewayPayment.prepare(first, terms()));
        GatewayPayment b = payments.saveAndFlush(GatewayPayment.prepare(second, terms()));
        GatewayPaymentHistory history = histories.saveAndFlush(GatewayPaymentHistory.registered(a));
        assertThat(a.getPgPaymentId()).isNotEqualTo(b.getPgPaymentId());
        assertThat(payments.findByPgPaymentIdAndMerchantMerchantId(a.getPgPaymentId(), second.getMerchantId())).isEmpty();
        assertThat(histories.findById(history.getHistoryId()).orElseThrow().getDetails()).isEmpty();
    }

    @Test
    void cardApprovalSnapshotAndLeasedNotificationAreMappedOnTheActualSchema() {
        GatewayPayment payment = payment(merchant(), "VERIFY-PG-CARD", PaymentMethod.CREDIT_CARD);
        dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalResponse card =
                new dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalResponse(payment.getPgPaymentId(),
                        "verify", CardCompany.SHINHAN, "a".repeat(64), "CARD-VERIFY", "4111-****-****-1111",
                        payment.getAmount(), LocalDateTime.of(2026, 1, 1, 12, 0), CardPaymentHistoryStatus.APPROVED);
        payment.approveCard(card.cardCompany(), card.approvedAt());
        histories.saveAndFlush(GatewayPaymentHistory.cardApproved(payment, card, "a".repeat(64)));
        Long id = notifications.saveAndFlush(GatewayCardNotification.pending(payment, card)).getNotificationId();
        entityManager.clear();
        GatewayCardNotification event = notifications.findForUpdate(id).orElseThrow();
        assertThat(event.getEventId()).isEqualTo(payment.getPgPaymentId() + ":APPROVE:INITIAL");
        assertThat(event.claim(LocalDateTime.now())).isNotNull();
        notifications.flush();
        event.delivered("ACCEPTED");
        notifications.flush();
        entityManager.clear();
        assertThat(notifications.findById(id).orElseThrow().getStatus()).isEqualTo("DELIVERED");
        assertThat(payments.findById(payment.getPgPaymentId()).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
    }

    @Test
    void notificationCompositeForeignKeyRejectsAnotherMerchant() {
        Merchant owner = merchant();
        Merchant other = merchant();
        GatewayPayment payment = payment(owner, "VERIFY-NOTIFY-OWNER", PaymentMethod.CREDIT_CARD);
        assertThatThrownBy(() -> jdbc.update("""
                insert into gateway_card_notification_outbox(event_id, pg_payment_id, merchant_id,
                    merchant_payment_no, buyer_reference, notification_contract, notification_target_key,
                    transaction_id, card_company, masked_card_number, amount, approved_at)
                values ('VERIFY-EVENT', ?, ?, 'VERIFY-NO', 'verify', 'TICKSY', 'verify',
                    'CARD-VERIFY', 'SHINHAN', '4111-****-****-1111', 100, CURRENT_TIMESTAMP)
                """, payment.getPgPaymentId(), other.getMerchantId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void actualUniqueConstraintRejectsTheSameMerchantNumber() {
        Merchant merchant = merchant();
        payments.saveAndFlush(GatewayPayment.prepare(merchant, terms()));
        assertThatThrownBy(() -> payments.saveAndFlush(GatewayPayment.prepare(merchant, terms())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void actualDatabaseRejectsDuplicatedOperationHistory() {
        GatewayPayment payment = payments.saveAndFlush(GatewayPayment.prepare(merchant(), terms()));
        histories.saveAndFlush(GatewayPaymentHistory.registered(payment));
        assertThatThrownBy(() -> histories.saveAndFlush(GatewayPaymentHistory.registered(payment)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void actualForeignKeyRejectsAnUnregisteredMerchant() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into gateway_payments(pg_payment_id, merchant_id, merchant_payment_no, buyer_reference,
                    payment_method, amount, status, creation_request_hash, expires_at)
                values (?, ?, 'VERIFY-ORPHAN', 'verify', 'CREDIT_CARD', 100, 'READY', ?, ?)
                """, UUID.randomUUID().toString(), Long.MAX_VALUE, "a".repeat(64), LocalDateTime.now().plusDays(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void actualFinancialColumnsAreNullableStringsAndConstrainedWithoutChangingTheCardLedger() {
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns where table_schema = 'public'
                  and table_name in ('dummy_card_payment_histories', 'dummy_virtual_accounts')
                  and column_name = 'pg_payment_id' and data_type = 'character varying'
                  and character_maximum_length = 44 and is_nullable = 'YES'
                """, Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                select count(*) from pg_constraint where conname in (
                  'fk_dummy_card_payment_histories_pg_payment', 'uk_dummy_card_payment_histories_pg_payment',
                  'fk_dummy_virtual_accounts_pg_payment', 'uk_dummy_virtual_accounts_pg_payment')
                """, Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns where table_schema = 'public'
                  and table_name = 'dummy_cards' and column_name = 'pg_payment_id'
                """, Long.class)).isZero();
    }

    @Test
    void actualFinancialRowsLinkToGatewayPaymentsAndOneCardHasSeveralApprovals() {
        Merchant merchant = merchant();
        DummyCard card = card();
        GatewayPayment first = payment(merchant, "VERIFY-CARD-1", PaymentMethod.CREDIT_CARD);
        GatewayPayment second = payment(merchant, "VERIFY-CARD-2", PaymentMethod.CREDIT_CARD);
        GatewayPayment bank = payment(merchant, "VERIFY-BANK", PaymentMethod.BANK_TRANSFER);
        assertThat(first.getPgPaymentId()).hasSize(44);
        assertThatCode(() -> LocalDate.parse(first.getPgPaymentId().substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE))
                .doesNotThrowAnyException();
        assertThat(UUID.fromString(first.getPgPaymentId().substring(8)).toString())
                .isEqualTo(first.getPgPaymentId().substring(8));
        approvals.saveAndFlush(approval(first.getPgPaymentId(), card, "VERIFY-CARD-1"));
        approvals.saveAndFlush(approval(second.getPgPaymentId(), card, "VERIFY-CARD-2"));
        DummyVirtualAccount account = accounts.saveAndFlush(account(bank.getPgPaymentId(), "VERIFY-BANK", "VERIFY-1000"));
        Long historyId = bankHistories.saveAndFlush(DummyVirtualAccountPaymentHistory.issued(account)).getHistoryId();
        entityManager.clear();

        assertThat(approvals.findByPgPaymentId(first.getPgPaymentId()).orElseThrow().getDummyCard().getDummyCardId())
                .isEqualTo(card.getDummyCardId());
        assertThat(approvals.findByPgPaymentId(second.getPgPaymentId()).orElseThrow().getDummyCard().getDummyCardId())
                .isEqualTo(card.getDummyCardId());
        assertThat(accounts.findByPgPaymentId(bank.getPgPaymentId()).orElseThrow().getAccountNumber()).isEqualTo("VERIFY-1000");
        assertThat(accounts.findPgPaymentIdByAccountNumber("VERIFY-1000")).contains(bank.getPgPaymentId());
        assertThat(bankHistories.findById(historyId).orElseThrow().getVirtualAccount().getPgPaymentId())
                .isEqualTo(bank.getPgPaymentId());
    }

    @Test
    void accountIssuanceStateAndJsonSnapshotAreStoredAndLegacyLookupsExcludeLinkedAccounts() {
        GatewayPayment payment = payment(merchant(), "VERIFY-ISSUANCE", PaymentMethod.BANK_TRANSFER);
        histories.saveAndFlush(GatewayPaymentHistory.registered(payment));
        DummyVirtualAccount account = accounts.saveAndFlush(account(payment.getPgPaymentId(), payment.getPgPaymentId(),
                "VERIFY-ISSUE-1000"));
        bankHistories.saveAndFlush(DummyVirtualAccountPaymentHistory.issued(account));
        payment.waitForDeposit();
        histories.saveAndFlush(GatewayPaymentHistory.accountIssued(payment, account.getAccountNumber(),
                BankCompany.KB.getBankName(), VirtualAccountPaymentStatus.WAITING_DEPOSIT.name(),
                GatewayPaymentStatus.READY));
        entityManager.clear();

        assertThat(payments.findById(payment.getPgPaymentId()).orElseThrow().getStatus())
                .isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        assertThat(histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(payment.getPgPaymentId(),
                GatewayPaymentOperation.ISSUE_ACCOUNT, "INITIAL").orElseThrow().getDetails())
                .containsEntry("accountNumber", "VERIFY-ISSUE-1000")
                .containsEntry("bankStatus", "WAITING_DEPOSIT");
        assertThat(accounts.findByPgPaymentId(payment.getPgPaymentId())).isPresent();
        assertThat(accounts.findByPaymentNo(payment.getPgPaymentId())).isEmpty();
        assertThat(accounts.findByPaymentNoForUpdate(payment.getPgPaymentId())).isEmpty();
        assertThat(accounts.findByAccountNumber(account.getAccountNumber())).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"CREDIT_CARD", "BANK_TRANSFER"})
    void actualForeignKeysRejectFinancialRowsWithAnUnknownGatewayPayment(PaymentMethod method) {
        String unknownId = UUID.randomUUID().toString();
        if (method == PaymentMethod.CREDIT_CARD) {
            DummyCard card = card();
            assertThatThrownBy(() -> approvals.saveAndFlush(approval(unknownId, card, "VERIFY-ORPHAN-CARD")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } else {
            assertThatThrownBy(() -> accounts.saveAndFlush(account(unknownId, "VERIFY-ORPHAN-BANK", "VERIFY-2000")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(value = PaymentMethod.class, names = {"CREDIT_CARD", "BANK_TRANSFER"})
    void actualUniqueConstraintsRejectTwoFinancialRowsForOneGatewayPayment(PaymentMethod method) {
        GatewayPayment payment = payment(merchant(), "VERIFY-DUPLICATE", method);
        if (method == PaymentMethod.CREDIT_CARD) {
            DummyCard card = card();
            approvals.saveAndFlush(approval(payment.getPgPaymentId(), card, "VERIFY-FIRST-CARD"));
            assertThatThrownBy(() -> approvals.saveAndFlush(approval(payment.getPgPaymentId(), card, "VERIFY-SECOND-CARD")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } else {
            accounts.saveAndFlush(account(payment.getPgPaymentId(), "VERIFY-FIRST-BANK", "VERIFY-3000"));
            assertThatThrownBy(() -> accounts.saveAndFlush(account(payment.getPgPaymentId(), "VERIFY-SECOND-BANK", "VERIFY-4000")))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void actualDatabasePreservesSeveralLegacyRowsWithoutGatewayLinks() {
        DummyCard card = card();
        for (int index = 1; index <= 2; index++) {
            approvals.saveAndFlush(DummyCardPaymentHistory.approved(card, "verify", "VERIFY-LEGACY-CARD-" + index,
                    "VERIFY-TX-" + index, "4111-****-****-1111", new BigDecimal("100.00")));
            accounts.saveAndFlush(DummyVirtualAccount.issue("VERIFY-LEGACY-BANK-" + index, BankCompany.KB,
                    "VERIFY-LEGACY-" + index, new BigDecimal("100.00"), LocalDateTime.of(2099, 1, 1, 23, 59, 59)));
        }
        entityManager.clear();

        assertThat(approvals.findByPaymentNo("VERIFY-LEGACY-CARD-1").orElseThrow().getPgPaymentId()).isNull();
        assertThat(approvals.findByPaymentNo("VERIFY-LEGACY-CARD-2").orElseThrow().getPgPaymentId()).isNull();
        assertThat(accounts.findPgPaymentIdByAccountNumber("VERIFY-LEGACY-1")).isEmpty();
        assertThat(accounts.findPgPaymentIdByAccountNumber("VERIFY-LEGACY-2")).isEmpty();
    }

    DummyCard card() {
        return cards.saveAndFlush(DummyCard.builder().userId("verify-card-owner").cardCompany(CardCompany.SHINHAN)
                .cardNumberHash(UUID.randomUUID().toString()).cardNumberLast4("1111")
                .cvcHash("test-cvc-hash").cardPasswordHash("test-password-hash").customerName("PG 검증 카드")
                .issuedAt(LocalDate.of(2020, 1, 1)).expiresAt(LocalDate.of(2099, 1, 1))
                .limitAmount(new BigDecimal("10000.00")).build());
    }

    GatewayPayment payment(Merchant merchant, String paymentNo, PaymentMethod method) {
        return payments.saveAndFlush(GatewayPayment.prepare(merchant, new GatewayPaymentTerms(paymentNo, "verify", method,
                new BigDecimal("100.00"), LocalDateTime.of(2099, 1, 1, 23, 59, 59),
                method == PaymentMethod.BANK_TRANSFER ? BankCompany.KB : null)));
    }

    DummyCardPaymentHistory approval(String pgPaymentId, DummyCard card, String paymentNo) {
        return DummyCardPaymentHistory.approved(pgPaymentId, card, "verify", paymentNo,
                "VERIFY-TX-" + UUID.randomUUID(), "4111-****-****-1111", new BigDecimal("100.00"));
    }

    DummyVirtualAccount account(String pgPaymentId, String paymentNo, String accountNumber) {
        return DummyVirtualAccount.issue(pgPaymentId, paymentNo, BankCompany.KB, accountNumber,
                new BigDecimal("100.00"), LocalDateTime.of(2099, 1, 1, 23, 59, 59));
    }

    Merchant merchant() {
        Merchant merchant = Merchant.register("PG 검증용 동일 이름", MerchantCredentialKey.hash("verify-" + UUID.randomUUID()), "TICKSY", "verify");
        merchant.activate();
        return merchants.saveAndFlush(merchant);
    }

    GatewayPaymentTerms terms() {
        return new GatewayPaymentTerms("VERIFY-SAME-NO", "verify", PaymentMethod.CREDIT_CARD,
                new BigDecimal("100.00"), LocalDateTime.of(2099, 1, 1, 23, 59, 59), null);
    }
}
