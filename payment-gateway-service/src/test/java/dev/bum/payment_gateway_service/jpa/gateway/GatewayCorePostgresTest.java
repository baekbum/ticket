package dev.bum.payment_gateway_service.jpa.gateway;

import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** 실제 DB에서는 DDL을 검증만 하고 각 테스트의 데이터 변경은 전부 롤백한다. */
@DataJpaTest(showSql = false, properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.sql.init.mode=never"})
@ContextConfiguration(classes = GatewayCorePostgresTest.Config.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "GATEWAY_CORE_VERIFY_URL", matches = "jdbc:postgresql:.*")
class GatewayCorePostgresTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Merchant.class, GatewayPayment.class})
    @EnableJpaRepositories(basePackageClasses = {MerchantJpaRepository.class, GatewayPaymentJpaRepository.class})
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

    @Test
    void migratedSchemaAndNonSecretTicksyRegistrationExist() {
        assertThat(merchants.findByCredentialKey("ticksy")).isPresent();
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
                """, UUID.randomUUID(), Long.MAX_VALUE, "a".repeat(64), LocalDateTime.now().plusDays(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    Merchant merchant() {
        Merchant merchant = Merchant.register("PG 검증용 동일 이름", "verify-" + UUID.randomUUID(), "TICKSY", "verify");
        merchant.activate();
        return merchants.saveAndFlush(merchant);
    }

    GatewayPaymentTerms terms() {
        return new GatewayPaymentTerms("VERIFY-SAME-NO", "verify", PaymentMethod.CREDIT_CARD,
                new BigDecimal("100.00"), LocalDateTime.of(2099, 1, 1, 23, 59, 59), null);
    }
}
