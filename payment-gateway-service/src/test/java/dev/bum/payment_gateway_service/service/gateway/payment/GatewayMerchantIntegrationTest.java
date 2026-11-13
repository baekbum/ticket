package dev.bum.payment_gateway_service.service.gateway.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.controller.advice.GlobalExceptionHandler;
import dev.bum.payment_gateway_service.controller.gateway.GatewayPaymentController;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.security.*;
import dev.bum.payment_gateway_service.security.merchant.*;
import dev.bum.payment_gateway_service.service.gateway.merchant.MerchantAuthenticationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = GatewayMerchantIntegrationTest.Config.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:gateway-merchants;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false", "spring.sql.init.mode=never",
        "app.ticket.service-token=test-merchant-a", "app.payment-provider.token=test-provider"
})
@AutoConfigureMockMvc
class GatewayMerchantIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = {Merchant.class, GatewayPayment.class})
    @EnableJpaRepositories(basePackageClasses = {MerchantJpaRepository.class, GatewayPaymentJpaRepository.class})
    @Import({SecurityConfig.class, GatewayPaymentController.class, GlobalExceptionHandler.class,
            GatewayPaymentService.class, GatewayPaymentCommandService.class, MerchantAuthenticationService.class,
            InternalServiceTokenValidator.class, PaymentProviderTokenValidator.class})
    static class Config { }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MerchantJpaRepository merchants;
    @Autowired GatewayPaymentJpaRepository payments;
    @Autowired GatewayPaymentService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean GatewayPaymentHistoryJpaRepository histories;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenStateStore tokenState;
    Merchant first;
    Merchant second;
    final LocalDateTime deadline = LocalDateTime.of(2099, 1, 1, 23, 59, 59);

    @BeforeEach
    void setup() {
        histories.deleteAll();
        payments.deleteAll();
        merchants.deleteAll();
        first = register("동일한 가맹점명", "ticksy");
        second = register("동일한 가맹점명", "melon");
    }

    @Test
    void identicalNamesAndPaymentNumbersAreIndependentAndCrossMerchantLookupIsHidden() throws Exception {
        JsonNode a = prepared("test-merchant-a", request("PAY-SAME", "4000"));
        JsonNode b = prepared("test-merchant-b", request("PAY-SAME", "4000"));
        assertThat(a.get("pgPaymentId").asText()).isNotEqualTo(b.get("pgPaymentId").asText());
        assertThat(first.getMerchantId()).isNotEqualTo(second.getMerchantId());
        mvc.perform(get("/api/v1/payments/gateway/" + a.get("pgPaymentId").asText())
                .header("Merchant", second.getMerchantId()).header("credentialKey", "test-merchant-b")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GATEWAY_PAYMENT_NOT_FOUND"));
        mvc.perform(get("/api/v1/payments/gateway/by-payment-no/PAY-SAME")
                .header("Merchant", second.getMerchantId()).header("credentialKey", "test-merchant-b")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pgPaymentId").value(b.get("pgPaymentId").asText()));
        assertThat(payments.count()).isEqualTo(2);
    }

    @Test
    void paymentIdentifiersUseTheKoreanDateAndUuidAndKeepLookupValidation() throws Exception {
        LocalDate beforeGeneration = LocalDate.now(ZoneId.of("Asia/Seoul"));
        JsonNode original = prepared("test-merchant-a", request("PAY-STRING-ID", "4000"));
        LocalDate afterGeneration = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String paymentId = original.get("pgPaymentId").asText();
        assertThat(paymentId).hasSize(44);
        assertThat(LocalDate.parse(paymentId.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE))
                .isIn(beforeGeneration, afterGeneration);
        assertThat(UUID.fromString(paymentId.substring(8)).toString()).isEqualTo(paymentId.substring(8));

        mvc.perform(get("/api/v1/payments/gateway/" + paymentId.toUpperCase(Locale.ROOT))
                .header("Merchant", first.getMerchantId()).header("credentialKey", "test-merchant-a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pgPaymentId").value(paymentId));
        mvc.perform(get("/api/v1/payments/gateway/not-a-uuid")
                .header("Merchant", first.getMerchantId()).header("credentialKey", "test-merchant-a")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/payments/gateway/20260230" + UUID.randomUUID())
                .header("Merchant", first.getMerchantId()).header("credentialKey", "test-merchant-a")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/payments/gateway/20261105" + "x".repeat(36))
                .header("Merchant", first.getMerchantId()).header("credentialKey", "test-merchant-a")).andExpect(status().isBadRequest());
    }

    @Test
    void existingUuidOnlyPaymentsRemainReadableWithoutReissuingTheirIdentifiers() throws Exception {
        String legacyId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                insert into gateway_payments(pg_payment_id,merchant_id,merchant_payment_no,buyer_reference,
                    payment_method,amount,refunded_amount,status,creation_request_hash,method_revision,
                    expires_at,version,created_at,updated_at)
                values (?,?,'PAY-LEGACY-ID','buyer','CREDIT_CARD',4000,0,'READY',?,0,?,0,?,?)
                """, legacyId, first.getMerchantId(), request("PAY-LEGACY-ID", "4000").toTerms().fingerprint(),
                deadline, now, now);

        mvc.perform(get("/api/v1/payments/gateway/" + legacyId.toUpperCase(Locale.ROOT))
                .header("Merchant", first.getMerchantId()).header("credentialKey", "test-merchant-a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pgPaymentId").value(legacyId));
        mvc.perform(get("/api/v1/payments/gateway/" + legacyId)
                .header("Merchant", second.getMerchantId()).header("credentialKey", "test-merchant-b")).andExpect(status().isNotFound());
        assertThat(payments.findById(legacyId)).isPresent();
    }

    @Test
    void replayNormalizesAmountAndDoesNotDuplicatePaymentOrHistory() throws Exception {
        JsonNode original = prepared("test-merchant-a", request("PAY-REPLAY", "4000"));
        JsonNode replay = prepared("test-merchant-a", request("PAY-REPLAY", "4000.0000"));
        assertThat(replay).isEqualTo(original);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(histories.count()).isEqualTo(1);
        assertThat(replay.has("credentialKey")).isFalse();
        assertThat(replay.has("creationRequestHash")).isFalse();
    }

    @Test
    void sameKeyRejectsChangedAmountBuyerMethodAndDeadline() throws Exception {
        prepared("test-merchant-a", request("PAY-CONFLICT", "4000"));
        List<GatewayPaymentPrepareRequest> changed = List.of(
                request("PAY-CONFLICT", "5000"),
                new GatewayPaymentPrepareRequest(null, "PAY-CONFLICT", "another", PaymentMethod.CREDIT_CARD,
                        new BigDecimal("4000"), deadline, null),
                new GatewayPaymentPrepareRequest(null, "PAY-CONFLICT", "buyer", PaymentMethod.BANK_TRANSFER,
                        new BigDecimal("4000"), deadline, BankCompany.KB),
                new GatewayPaymentPrepareRequest(null, "PAY-CONFLICT", "buyer", PaymentMethod.CREDIT_CARD,
                        new BigDecimal("4000"), deadline.plusSeconds(1), null));
        for (var request : changed) {
            prepare("test-merchant-a", request).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("GATEWAY_PAYMENT_REQUEST_MISMATCH"));
        }
        assertThat(payments.count()).isEqualTo(1);
        assertThat(histories.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "USER")
    void userRoleProviderTokenAndBodyMerchantIdCannotReplaceMerchantCredentials() throws Exception {
        mvc.perform(post("/api/v1/payments/gateway/prepare").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request("PAY-AUTH", "4000")))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/payments/gateway/" + UUID.randomUUID())
                .header("X-Payment-Provider-Token", "test-provider")).andExpect(status().isUnauthorized());
        prepare("invalid", request("PAY-AUTH", "4000")).andExpect(status().isUnauthorized());
        prepare("test-unregistered", request("PAY-AUTH", "4000")).andExpect(status().isUnauthorized());
        var spoofed = new GatewayPaymentPrepareRequest(second.getMerchantId(), "PAY-AUTH", "buyer",
                PaymentMethod.CREDIT_CARD, new BigDecimal("4000"), deadline, null);
        prepare("test-merchant-a", spoofed).andExpect(status().isForbidden());
        assertThat(payments.count()).isZero();
    }

    @Test
    void anotherMerchantCredentialCannotUseLegacyUnscopedFinancialApis() throws Exception {
        for (String path : List.of("card/refund", "virtual-account/cancel", "virtual-account/issue", "refunds/internal/resolve")) {
            mvc.perform(post("/api/v1/payments/" + path).header("X-Service-Token", "test-merchant-b"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void suspendedMerchantCanReadExistingPaymentButCannotPrepare() throws Exception {
        JsonNode original = prepared("test-merchant-a", request("PAY-SUSPEND", "4000"));
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
                merchants.findById(first.getMerchantId()).orElseThrow().suspend());
        prepare("test-merchant-a", request("PAY-NEW", "4000")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payments/gateway/" + original.get("pgPaymentId").asText())
                .header("Merchant", first.getMerchantId()).header("credentialKey", "test-merchant-a")).andExpect(status().isOk());
    }

    @Test
    void rotatingTheDatabaseHashInvalidatesTheOldKeyWithoutRestarting() throws Exception {
        JsonNode original = prepared("test-merchant-a", request("PAY-ROTATE", "4000"));
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
                merchants.findById(first.getMerchantId()).orElseThrow()
                        .changeCredentialHash(MerchantCredentialKey.hash("test-merchant-a-old")));
        prepare("test-merchant-a", request("PAY-ROTATE", "4000")).andExpect(status().isUnauthorized());
        JsonNode replay = prepared("test-merchant-a-old", request("PAY-ROTATE", "4000"));
        assertThat(replay).isEqualTo(original);
    }

    @Test
    void missingAmbiguousAndMismatchedMerchantHeadersCannotAuthenticate() throws Exception {
        String path = "/api/v1/payments/gateway/" + UUID.randomUUID();
        mvc.perform(get(path).header("credentialKey", "test-merchant-a")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Merchant", first.getMerchantId())).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("X-Service-Token", "test-merchant-a")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Merchant", second.getMerchantId()).header("credentialKey", "test-merchant-a"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Merchant", first.getMerchantId(), second.getMerchantId())
                .header("credentialKey", "test-merchant-a")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Merchant", first.getMerchantId())
                .header("credentialKey", "test-merchant-a", "test-merchant-b")).andExpect(status().isUnauthorized());
        for (String value : List.of("ticksy", "0", "-1", "01", "1,2", "9223372036854775808")) {
            mvc.perform(get(path).header("Merchant", value).header("credentialKey", "test-merchant-a"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void anUnprovisionedOrRevokedMerchantCannotAuthenticate() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored ->
                merchants.findById(first.getMerchantId()).orElseThrow().changeCredentialHash(null));
        prepare("test-merchant-a", request("PAY-REVOKED", "4000")).andExpect(status().isUnauthorized());
        assertThat(payments.count()).isZero();
    }

    @Test
    void historyStorageFailureRollsBackTheNewPayment() {
        doThrow(new DataIntegrityViolationException("simulated history failure")).when(histories).saveAndFlush(any());
        assertThatThrownBy(() -> service.prepare(principal(), request("PAY-ROLLBACK", "4000")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(payments.count()).isZero();
        assertThat(histories.count()).isZero();
    }

    @Test
    void concurrentSameRequestReturnsOneCommittedPaymentAndHistory() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<GatewayPaymentResponse>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return service.prepare(principal(), request("PAY-CONCURRENT", "4000"));
                }));
            }
            start.countDown();
            Set<String> ids = new HashSet<>();
            for (var future : futures) ids.add(future.get(20, TimeUnit.SECONDS).pgPaymentId());
            assertThat(ids).hasSize(1);
            assertThat(payments.count()).isEqualTo(1);
            assertThat(histories.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentDifferentAmountsHaveOneWinnerAndOneConflict() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (String amount : List.of("4000", "5000")) {
                futures.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        service.prepare(principal(), request("PAY-CONCURRENT-CHANGE", amount));
                        return "CREATED";
                    } catch (GatewayPaymentConflictException expected) {
                        return "CONFLICT";
                    }
                }));
            }
            start.countDown();
            assertThat(List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED", "CONFLICT");
            assertThat(payments.count()).isEqualTo(1);
            assertThat(histories.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void invalidMethodBankPrecisionAndExpiredNewRequestsAreRejected() throws Exception {
        List<GatewayPaymentPrepareRequest> invalid = List.of(
                request("PAY-INVALID", "1.234"),
                new GatewayPaymentPrepareRequest(null, "PAY-INVALID", "buyer", PaymentMethod.KAKAO_PAY,
                        new BigDecimal("4000"), deadline, null),
                new GatewayPaymentPrepareRequest(null, "PAY-INVALID", "buyer", PaymentMethod.BANK_TRANSFER,
                        new BigDecimal("4000"), deadline, null),
                new GatewayPaymentPrepareRequest(null, "PAY-INVALID", "buyer", PaymentMethod.CREDIT_CARD,
                        new BigDecimal("4000"), LocalDateTime.now().minusDays(1), null));
        for (var request : invalid) prepare("test-merchant-a", request).andExpect(status().isBadRequest());
        assertThat(payments.count()).isZero();
    }

    Merchant register(String name, String key) {
        Merchant merchant = Merchant.register(name, MerchantCredentialKey.hash("ticksy".equals(key) ? "test-merchant-a" : "test-merchant-b"), "TICKSY", key);
        merchant.activate();
        return merchants.saveAndFlush(merchant);
    }

    AuthenticatedMerchant principal() {
        return new AuthenticatedMerchant(first.getMerchantId());
    }

    GatewayPaymentPrepareRequest request(String no, String amount) {
        return new GatewayPaymentPrepareRequest(null, no, "buyer", PaymentMethod.CREDIT_CARD,
                new BigDecimal(amount), deadline, null);
    }

    ResultActions prepare(String token, GatewayPaymentPrepareRequest request) throws Exception {
        return mvc.perform(post("/api/v1/payments/gateway/prepare").header("Merchant", "test-merchant-b".equals(token) ? second.getMerchantId() : first.getMerchantId())
                .header("credentialKey", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)));
    }

    JsonNode prepared(String token, GatewayPaymentPrepareRequest request) throws Exception {
        return json.readTree(prepare(token, request).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
}
