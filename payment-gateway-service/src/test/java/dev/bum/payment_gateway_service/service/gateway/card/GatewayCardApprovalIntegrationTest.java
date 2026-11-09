package dev.bum.payment_gateway_service.service.gateway.card;

import com.fasterxml.jackson.databind.*;
import dev.bum.common.service.paymentgateway.notification.CardPaymentNotification;
import dev.bum.common.service.paymentgateway.notification.CardPaymentNotificationResponse;
import com.sun.net.httpserver.HttpServer;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.config.*;
import dev.bum.payment_gateway_service.controller.advice.*;
import dev.bum.payment_gateway_service.controller.financial.CardController;
import dev.bum.payment_gateway_service.controller.gateway.*;
import dev.bum.payment_gateway_service.dto.card.*;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.jpa.card.*;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.security.*;
import dev.bum.payment_gateway_service.security.merchant.MerchantCredentialResolver;
import dev.bum.payment_gateway_service.service.financial.card.*;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import dev.bum.payment_gateway_service.service.gateway.merchant.MerchantAuthenticationService;
import dev.bum.payment_gateway_service.service.gateway.notification.*;
import dev.bum.payment_gateway_service.service.gateway.payment.*;
import dev.bum.payment_gateway_service.support.CardCredentials;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = GatewayCardApprovalIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:gateway-card-approve;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false", "spring.sql.init.mode=never",
        "app.financial-simulation.base-url=", "app.financial-simulation.token=", "management.tracing.enabled=false",
        "app.merchants.credentials.ticksy[0]=test-merchant-a", "app.merchants.credentials.melon[0]=test-merchant-b",
        "app.ticket.service-token=test-merchant-a", "app.payment-provider.token=test-provider"
})
@AutoConfigureMockMvc
class GatewayCardApprovalIntegrationTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "dev.bum.payment_gateway_service.jpa")
    @EnableJpaRepositories(basePackages = "dev.bum.payment_gateway_service.jpa")
    @Import({GatewayCardApiService.class, SecurityConfig.class, InternalServiceTokenValidator.class, PaymentProviderTokenValidator.class,
            MerchantAuthenticationService.class, MerchantCredentialResolver.class, MerchantCredentialProperties.class,
            FinancialSimulationHttpConfig.class, FinancialSimulationGatewayService.class, GatewayPaymentService.class,
            GatewayPaymentCommandService.class, GatewayCardApprovalService.class, GatewayCardApprovalCommandService.class,
            CardApprovalService.class, CardApprovalCommandService.class, GatewayPaymentController.class,
            GatewayCardPaymentController.class, CardController.class, GlobalExceptionHandler.class,
            FinancialSimulationExceptionHandler.class, MerchantNotificationTokenCipher.class,
            MerchantNotificationEndpointService.class,
            MerchantNotificationTargetResolver.class, StandardCardNotificationAdapter.class,
            GatewayCardNotificationCommands.class, GatewayCardNotificationDispatcher.class})
    static class Config {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MerchantJpaRepository merchants;
    @Autowired GatewayPaymentJpaRepository payments;
    @Autowired DummyCardJpaRepository cards;
    @Autowired PasswordEncoder encoder;
    @Autowired MerchantNotificationEndpointJpaRepository destinations;
    @Autowired MerchantNotificationEndpointService destinationService;
    @Autowired List<MerchantCardNotificationAdapter> senders;
    @Autowired GatewayCardNotificationDispatcher dispatcher;
    @Autowired GatewayCardNotificationCommands deliveryCommands;
    @Autowired GatewayCardApprovalService pgApprovals;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired FinancialSimulationToken internalToken;
    @MockitoSpyBean GatewayPaymentHistoryJpaRepository histories;
    @MockitoSpyBean DummyCardPaymentHistoryJpaRepository approvals;
    @MockitoSpyBean GatewayCardNotificationJpaRepository notifications;
    @MockitoSpyBean FinancialSimulationGatewayService financial;
    @MockitoSpyBean StandardCardNotificationAdapter adapter;
    @MockitoBean GatewayCardPaymentService legacyCards;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenStateStore tokenStateStore;

    final String pan = "4111111111111111";
    final String anotherPan = "5555555555554444";
    final LocalDateTime deadline = LocalDateTime.of(2099, 1, 1, 23, 59, 59);
    Merchant first;
    Merchant second;
    DummyCard ledger;
    HttpServer merchantServer;
    final Queue<Callback> callbacks = new ConcurrentLinkedQueue<>();
    final AtomicReference<String> mode = new AtomicReference<>("ACCEPTED");
    record Callback(String path, String eventId, String pgId, CardPaymentNotification body) { }

    @BeforeEach
    void setup() throws Exception {
        notifications.deleteAll();
        histories.deleteAll();
        approvals.deleteAll();
        payments.deleteAll();
        cards.deleteAll();
        destinations.deleteAll();
        merchants.deleteAll();
        first = merchant("ticksy", "a");
        second = merchant("melon", "b");
        ledger = card(pan, "1000");
        card(anotherPan, "1000");
        callbacks.clear();
        mode.set("ACCEPTED");
        merchantServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        merchantServer.createContext("/", exchange -> {
            CardPaymentNotification body = json.readValue(exchange.getRequestBody(), CardPaymentNotification.class);
            String path = exchange.getRequestURI().getPath();
            callbacks.add(new Callback(path, exchange.getRequestHeaders().getFirst("X-PG-Event-Id"),
                    exchange.getRequestHeaders().getFirst("X-PG-Payment-Id"), body));
            boolean authorized = ("callback-" + (path.startsWith("/a/") ? "a" : "b"))
                    .equals(exchange.getRequestHeaders().getFirst("X-Service-Token"));
            if (!authorized || mode.get().equals("ERROR")) {
                exchange.sendResponseHeaders(authorized ? 503 : 403, -1);
                exchange.close();
                return;
            }
            if (mode.get().equals("REDIRECT")) {
                exchange.getResponseHeaders().set("Location", "/b/payment-events/v1");
                exchange.sendResponseHeaders(307, -1);
                exchange.close();
                return;
            }
            boolean rejected = mode.get().equals("REJECTED");
            CardPaymentNotificationResponse result = new CardPaymentNotificationResponse(
                    mode.get().equals("BAD_VERSION") ? 2 : body.version(),
                    mode.get().equals("BAD_EVENT") ? "OTHER" : body.eventId(),
                    mode.get().equals("BAD_MERCHANT") ? -1L : body.merchantId(),
                    mode.get().equals("BAD_PG") ? "OTHER" : body.pgPaymentId(),
                    mode.get().equals("MISMATCH") ? "OTHER" : body.merchantPaymentNo(),
                    mode.get().equals("BAD_TRANSACTION") ? "OTHER" : body.transactionId(),
                    mode.get().equals("BAD_AMOUNT") ? BigDecimal.ZERO : body.amount(),
                    mode.get().equals("BAD_OUTCOME") ? null : rejected ? CardPaymentNotificationResponse.Outcome.REJECTED
                            : CardPaymentNotificationResponse.Outcome.ACCEPTED);
            byte[] response = json.writeValueAsBytes(result);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        merchantServer.start();
        for (String key : List.of("a", "b")) {
            destinationService.register(key.equals("a") ? first.getMerchantId() : second.getMerchantId(), key,
                    "http://127.0.0.1:" + merchantServer.getAddress().getPort()
                            + (key.equals("a") ? "/a/hooks/payment-update" : "/b/payment-events/v1"), "callback-" + key);
        }
    }

    @AfterEach
    void stopMerchant() { merchantServer.stop(0); }

    @Test
    void approvalLinksTheFinancialTransactionAndAtomicallyStoresPgStateHistoryAndNotification() throws Exception {
        String id = prepare("test-merchant-a", "PAY-CARD", "100", PaymentMethod.CREDIT_CARD);
        JsonNode result = approved("test-merchant-a", id);
        assertThat(result.get("pgStatus").asText()).isEqualTo("PAID");
        assertThat(result.get("notificationStatus").asText()).isEqualTo("PENDING");
        assertThat(result.get("paymentNo").asText()).isEqualTo("PAY-CARD");
        DummyCardPaymentHistory approval = approvals.findByPgPaymentId(id).orElseThrow();
        assertThat(approval.getPaymentNo()).isEqualTo(id);
        assertThat(approval.getStatus()).isEqualTo(CardPaymentHistoryStatus.APPROVED);
        assertThat(used()).isEqualByComparingTo("100");
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(2);
        assertThat(notifications.count()).isEqualTo(1);
        assertThat(json.writeValueAsString(histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(
                id, GatewayPaymentOperation.APPROVE, "INITIAL").orElseThrow().getDetails())).doesNotContain(pan, "cvc", "cardPassword");
        assertThat(callbacks).isEmpty();
        verifyNoInteractions(legacyCards);
    }

    @Test
    void cachedReplayWorksWithoutTheCardServerAndRejectsChangedCardOrAmount() throws Exception {
        String id = prepare("test-merchant-a", "PAY-REPLAY", "100", PaymentMethod.CREDIT_CARD);
        JsonNode original = approved("test-merchant-a", id);
        doThrow(new ResourceAccessException("offline")).when(financial).gatewayCardStatus(anyString());
        assertThat(approved("test-merchant-a", id.toUpperCase(Locale.ROOT))).isEqualTo(original);
        approve("test-merchant-a", id, request("200", pan)).andExpect(status().isConflict());
        approve("test-merchant-a", id, request("100", anotherPan)).andExpect(status().isConflict());
        assertThat(used()).isEqualByComparingTo("100");
        verify(financial, times(1)).approveGatewayCard(any());
    }

    @Test
    void sameMerchantNumbersAreIndependentAndNotificationsUseTheirRegisteredTargets() throws Exception {
        String a = prepare("test-merchant-a", "SAME-NO", "100", PaymentMethod.CREDIT_CARD);
        String b = prepare("test-merchant-b", "SAME-NO", "100", PaymentMethod.CREDIT_CARD);
        approved("test-merchant-a", a);
        approved("test-merchant-b", b);
        dispatcher.retryPending();
        assertThat(callbacks).hasSize(2);
        assertThat(senders).hasSize(1);
        assertThat(senders.getFirst()).isInstanceOf(StandardCardNotificationAdapter.class);
        assertThat(callbacks.stream().map(Callback::path))
                .containsExactlyInAnyOrder("/a/hooks/payment-update", "/b/payment-events/v1");
        assertThat(callbacks).allSatisfy(callback -> {
            assertThat(callback.eventId()).isEqualTo(callback.body().eventId());
            assertThat(callback.pgId()).isEqualTo(callback.body().pgPaymentId());
            assertThat(callback.body().version()).isEqualTo(1);
            assertThat(callback.body().status()).isEqualTo(PaymentStatus.PAID);
            assertThat(callback.body().paymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        });
        assertThat(callbacks.stream().map(callback -> callback.body().merchantPaymentNo())).containsOnly("SAME-NO");
        assertThat(callbacks.stream().map(Callback::pgId)).containsExactlyInAnyOrder(a, b);
        assertThat(used()).isEqualByComparingTo("200");
        assertThat(notifications.findAll()).allMatch(event -> event.getStatus().equals("DELIVERED"));
        assertThat(approvals.findByPgPaymentId(a).orElseThrow().getStatus()).isEqualTo(CardPaymentHistoryStatus.APPROVED);
    }

    @Test
    void merchantAndBuyerAmountAndStoredMethodAreCheckedBeforeFinancialExecution() throws Exception {
        String id = prepare("test-merchant-a", "PAY-ACCESS", "100", PaymentMethod.CREDIT_CARD);
        approve("test-merchant-b", id, request("100", pan)).andExpect(status().isNotFound());
        approve("invalid", id, request("100", pan)).andExpect(status().isUnauthorized());
        approve("test-merchant-a", id, new GatewayCardApprovalRequest(second.getMerchantId(), "buyer", CardCompany.SHINHAN,
                pan, "123", "12", new BigDecimal("100"))).andExpect(status().isForbidden());
        approve("test-merchant-a", id, new GatewayCardApprovalRequest(null, "other", CardCompany.SHINHAN,
                pan, "123", "12", new BigDecimal("100"))).andExpect(status().isConflict());
        approve("test-merchant-a", id, request("200", pan)).andExpect(status().isConflict());
        String bank = prepare("test-merchant-a", "PAY-BANK", "100", PaymentMethod.BANK_TRANSFER);
        approve("test-merchant-a", bank, request("100", pan)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/payments/card/gateway/" + id).header("X-Service-Token", "test-merchant-b"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/internal/financial-simulations/cards/gateway/approve")
                .header("X-Service-Token", "test-merchant-a").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(approvals.count()).isZero();
        verify(financial, never()).approveGatewayCard(any());
    }

    @Test
    void concurrentApprovalForOnePgIdChargesOnce() throws Exception {
        String id = prepare("test-merchant-a", "PAY-CONCURRENT", "100", PaymentMethod.CREDIT_CARD);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                results.add(pool.submit(() -> approved("test-merchant-a", id).get("transactionId").asText()));
            }
            Set<String> numbers = new HashSet<>();
            for (Future<String> result : results) numbers.add(result.get(30, TimeUnit.SECONDS));
            assertThat(numbers).hasSize(1);
        } finally { pool.shutdownNow(); }
        assertThat(approvals.count()).isEqualTo(1);
        assertThat(notifications.count()).isEqualTo(1);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(2);
        assertThat(used()).isEqualByComparingTo("100");
    }

    @Test
    void differentConcurrentPgPaymentsCannotOverrunTheSameCardLimit() throws Exception {
        String a = prepare("test-merchant-a", "PAY-LIMIT-A", "600", PaymentMethod.CREDIT_CARD);
        String b = prepare("test-merchant-a", "PAY-LIMIT-B", "600", PaymentMethod.CREDIT_CARD);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = pool.submit(() -> approve("test-merchant-a", a, request("600", pan))
                    .andReturn().getResponse().getStatus());
            Future<Integer> second = pool.submit(() -> approve("test-merchant-a", b, request("600", pan))
                    .andReturn().getResponse().getStatus());
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 400);
        } finally { pool.shutdownNow(); }
        assertThat(used()).isEqualByComparingTo("600");
        assertThat(approvals.count()).isEqualTo(1);
    }

    @Test
    void invalidCardCredentialsAndCardStateLeavePgReadyAndDoNotConsumeLimit() throws Exception {
        String id = prepare("test-merchant-a", "PAY-DECLINED", "100", PaymentMethod.CREDIT_CARD);
        for (GatewayCardApprovalRequest request : List.of(
                new GatewayCardApprovalRequest(null, "buyer", CardCompany.SHINHAN, pan, "wrong", "12", new BigDecimal("100")),
                new GatewayCardApprovalRequest(null, "buyer", CardCompany.SHINHAN, pan, "123", "wrong", new BigDecimal("100")),
                request("100", "0000000000000000"))) {
            approve("test-merchant-a", id, request).andExpect(status().isBadRequest());
        }
        new TransactionTemplate(transactions).executeWithoutResult(status -> cards.findById(ledger.getDummyCardId())
                .orElseThrow().deactivate());
        approve("test-merchant-a", id, request("100", pan)).andExpect(status().isBadRequest());
        assertThat(approvals.count()).isZero();
        assertThat(used()).isEqualByComparingTo("0");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.READY);
    }

    @Test
    void cardHistoryFailureRollsBackTheLimitAndApproval() throws Exception {
        String id = prepare("test-merchant-a", "PAY-FINANCE-FAIL", "100", PaymentMethod.CREDIT_CARD);
        doThrow(new DataIntegrityViolationException("simulated")).when(approvals).saveAndFlush(any());
        approve("test-merchant-a", id, request("100", pan)).andExpect(status().isServiceUnavailable());
        assertThat(used()).isEqualByComparingTo("0");
        assertThat(approvals.count()).isZero();
        assertThat(notifications.count()).isZero();
    }

    @Test
    void outboxStorageFailureRollsBackPgStateAndHistoryAndRetryRecoversFinancialApproval() throws Exception {
        String id = prepare("test-merchant-a", "PAY-OUTBOX-FAIL", "100", PaymentMethod.CREDIT_CARD);
        doThrow(new DataIntegrityViolationException("simulated")).when(notifications).saveAndFlush(any());
        approve("test-merchant-a", id, request("100", pan)).andExpect(status().isServiceUnavailable());
        assertThat(used()).isEqualByComparingTo("100");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.READY);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(1);
        assertThat(notifications.count()).isZero();
        reset(notifications);
        approve("test-merchant-a", id, request("100", anotherPan)).andExpect(status().isConflict());
        approved("test-merchant-a", id);
        assertThat(used()).isEqualByComparingTo("100");
        verify(financial, times(1)).approveGatewayCard(any());
    }

    @Test
    void aLostFinancialResponseIsRecoveredByQueryWithoutReapproval() throws Exception {
        String id = prepare("test-merchant-a", "PAY-LOST", "100", PaymentMethod.CREDIT_CARD);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new ResourceAccessException("simulated response loss");
        }).when(financial).approveGatewayCard(any());
        approve("test-merchant-a", id, request("100", pan)).andExpect(status().isServiceUnavailable());
        approved("test-merchant-a", id);
        verify(financial, times(1)).approveGatewayCard(any());
        assertThat(used()).isEqualByComparingTo("100");
    }

    @Test
    void notificationFailureOrMismatchedResponseDoesNotFailTheFinancialPayment() throws Exception {
        String id = prepare("test-merchant-a", "PAY-HTTP-FAIL", "100", PaymentMethod.CREDIT_CARD);
        approved("test-merchant-a", id);
        for (String failure : List.of("ERROR", "MISMATCH", "BAD_VERSION", "BAD_EVENT", "BAD_MERCHANT",
                "BAD_PG", "BAD_TRANSACTION", "BAD_AMOUNT", "BAD_OUTCOME")) {
            mode.set(failure);
            retryNow(id);
            dispatcher.retryPending();
            assertThat(event(id).getStatus()).isEqualTo("PENDING");
            assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
            assertThat(used()).isEqualByComparingTo("100");
        }
        mode.set("ACCEPTED");
        retryNow(id);
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(callbacks.stream().map(Callback::eventId).distinct()).hasSize(1);
    }

    @Test
    void theRegisteredFullUrlIsUsedWithoutFollowingRedirects() throws Exception {
        String id = prepare("test-merchant-a", "PAY-REDIRECT", "100", PaymentMethod.CREDIT_CARD);
        approved("test-merchant-a", id);
        mode.set("REDIRECT");
        dispatcher.retryPending();
        assertThat(callbacks).hasSize(1);
        assertThat(callbacks.peek().path()).isEqualTo("/a/hooks/payment-update");
        assertThat(event(id).getStatus()).isEqualTo("PENDING");
        assertThat(used()).isEqualByComparingTo("100");
    }

    @Test
    void thePaymentRequestCannotOverrideTheRegisteredMerchantUrl() throws Exception {
        String id = prepare("test-merchant-a", "PAY-REQUEST-URL", "100", PaymentMethod.CREDIT_CARD);
        com.fasterxml.jackson.databind.node.ObjectNode body = json.valueToTree(request("100", pan));
        body.put("notificationUrl", destinations.findByMerchantMerchantIdAndTargetKey(second.getMerchantId(), "b")
                .orElseThrow().getNotificationUrl());
        mvc.perform(post("/api/v1/payments/card/gateway/" + id + "/approve").header("X-Service-Token", "test-merchant-a")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().isOk());
        dispatcher.retryPending();
        assertThat(callbacks).hasSize(1);
        assertThat(callbacks.peek().path()).isEqualTo("/a/hooks/payment-update");
        assertThat(callbacks.peek().body().merchantId()).isEqualTo(first.getMerchantId());
    }

    @Test
    void disabledDbTargetRetainsTheEventAndResumesWithoutRechargingTheCard() throws Exception {
        String id = prepare("test-merchant-a", "PAY-DB-DISABLED", "100", PaymentMethod.CREDIT_CARD);
        approved("test-merchant-a", id);
        destinationService.changeActive(first.getMerchantId(), "a", false);
        dispatcher.retryPending();
        assertThat(callbacks).isEmpty();
        assertThat(event(id).getStatus()).isEqualTo("PENDING");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        destinationService.changeActive(first.getMerchantId(), "a", true);
        retryNow(id);
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(callbacks.peek().eventId()).isEqualTo(id + ":APPROVE:INITIAL");
        assertThat(used()).isEqualByComparingTo("100");
        assertThat(approvals.count()).isEqualTo(1);
    }

    @Test
    void dbUrlChangesTakeEffectWithoutRestartAndKeepMerchantOwnership() throws Exception {
        String id = prepare("test-merchant-a", "PAY-DB-UPDATED", "100", PaymentMethod.CREDIT_CARD);
        approved("test-merchant-a", id);
        destinationService.update(first.getMerchantId(), "a", "http://127.0.0.1:"
                + merchantServer.getAddress().getPort() + "/a/new-notification-endpoint", "callback-a");
        assertThatThrownBy(() -> destinationService.update(second.getMerchantId(), "a",
                "https://other.example/hook", "other-secret")).isInstanceOf(IllegalArgumentException.class);
        dispatcher.retryPending();
        assertThat(callbacks.peek().path()).isEqualTo("/a/new-notification-endpoint");
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(destinations.findByMerchantMerchantIdAndTargetKey(first.getMerchantId(), "a")
                .orElseThrow().getServiceTokenCiphertext()).startsWith("v1:").doesNotContain("callback-a");
    }

    @Test
    void merchantMayRegisterTheSameTargetKeyButCannotDuplicateItsOwnKey() {
        destinationService.register(second.getMerchantId(), "a", "https://second.example/hook", "second-secret");
        assertThat(destinations.findByMerchantMerchantIdAndTargetKey(first.getMerchantId(), "a")).isPresent();
        assertThat(destinations.findByMerchantMerchantIdAndTargetKey(second.getMerchantId(), "a")).isPresent();
        assertThatThrownBy(() -> destinationService.register(first.getMerchantId(), "a",
                "https://duplicate.example/hook", "secret")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void notificationResponseLossReusesTheSameEventAndTransaction() throws Exception {
        String id = prepare("test-merchant-a", "PAY-NOTIFY-LOST", "100", PaymentMethod.CREDIT_CARD);
        approved("test-merchant-a", id);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new ResourceAccessException("simulated merchant response loss");
        }).doCallRealMethod().when(adapter).send(any(), anyString(), any());
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("PENDING");
        retryNow(id);
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(callbacks).hasSize(2);
        assertThat(callbacks.stream().map(Callback::eventId).distinct()).hasSize(1);
        assertThat(callbacks.stream().map(callback -> callback.body().transactionId()).distinct()).hasSize(1);
        assertThat(used()).isEqualByComparingTo("100");
    }

    @Test
    void definitiveMerchantRejectionCancelsTheCardAndPgExactlyOnce() throws Exception {
        String id = prepare("test-merchant-a", "PAY-REJECTED", "100", PaymentMethod.CREDIT_CARD);
        approved("test-merchant-a", id);
        mode.set("REJECTED");
        dispatcher.retryPending();
        dispatcher.retryPending();
        assertThat(event(id).getOutcome()).isEqualTo("REJECTED");
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.CANCELLED);
        assertThat(approvals.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(CardPaymentHistoryStatus.CANCELLED);
        assertThat(used()).isEqualByComparingTo("0");
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
        verify(financial, times(1)).cancelGatewayCard(id);
    }

    @Test
    void lostCancellationResponseIsRecoveredWithoutRestoringLimitTwice() throws Exception {
        String id = prepare("test-merchant-a", "PAY-CANCEL-LOST", "100", PaymentMethod.CREDIT_CARD);
        approved("test-merchant-a", id);
        mode.set("REJECTED");
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new ResourceAccessException("simulated cancellation response loss");
        }).doCallRealMethod().when(financial).cancelGatewayCard(id);
        dispatcher.retryPending();
        assertThat(used()).isEqualByComparingTo("0");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        retryNow(id);
        dispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.CANCELLED);
        assertThat(used()).isEqualByComparingTo("0");
    }

    @Test
    void completedDeliveryStorageFailureRetriesTheSameEvent() throws Exception {
        String id = prepare("test-merchant-a", "PAY-ACK-FAIL", "100", PaymentMethod.CREDIT_CARD);
        approved("test-merchant-a", id);
        doThrow(new DataIntegrityViolationException("simulated completed delivery persistence failure"))
                .when(notifications).saveAndFlush(any());
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("PENDING");
        reset(notifications);
        retryNow(id);
        dispatcher.retryPending();
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(callbacks).hasSize(2);
        assertThat(used()).isEqualByComparingTo("100");
    }

    @Test
    void expiredLeasesCanBeReclaimedAndOldResponsesCannotOverwriteTheNewAttempt() throws Exception {
        String id = prepare("test-merchant-a", "PAY-LEASE", "100", PaymentMethod.CREDIT_CARD);
        approved("test-merchant-a", id);
        CardNotificationSnapshot old = deliveryCommands.claim(event(id).getNotificationId());
        assertThat(deliveryCommands.claim(old.notificationId())).isNull();
        jdbc.update("update gateway_card_notification_outbox set lease_until = ? where notification_id = ?",
                LocalDateTime.now().minusMinutes(1), old.notificationId());
        CardNotificationSnapshot current = deliveryCommands.claim(old.notificationId());
        deliveryCommands.complete(old, null);
        assertThat(event(id).getStatus()).isEqualTo("SENDING");
        deliveryCommands.complete(current, null);
        deliveryCommands.failed(old, "late");
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void legacyPaymentNumberQueriesAndRecoveryCannotConsumeLinkedCardTransactions() throws Exception {
        String id = prepare("test-merchant-b", "PAY-LEGACY-ISOLATED", "100", PaymentMethod.CREDIT_CARD);
        approved("test-merchant-b", id);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            assertThat(approvals.findByPaymentNo(id)).isEmpty();
            assertThat(approvals.findByPaymentNoAndTransactionId(id, event(id).getTransactionId())).isEmpty();
            assertThat(approvals.findByPaymentNoForUpdate(id)).isEmpty();
            assertThat(approvals.existsByPaymentNo(id)).isFalse();
        });
        assertThat(approvals.findPendingPaymentNos(List.of(CardPaymentHistoryStatus.APPROVED),
                org.springframework.data.domain.PageRequest.of(0, 100))).isEmpty();
        verifyNoInteractions(legacyCards);
    }

    @Test
    void financialHttpAndNotificationDispatchAreForbiddenInsideAnOuterTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status ->
                pgApprovals.status(null, UUID.randomUUID().toString())))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status ->
                dispatcher.retryPending())).isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void pgHistoryFailurePreservesFinancialApprovalAndRecoversAfterMerchantSuspension() throws Exception {
        String id = prepare("test-merchant-a", "PAY-PG-HISTORY-FAIL", "100", PaymentMethod.CREDIT_CARD);
        doThrow(new DataIntegrityViolationException("simulated PG history failure")).when(histories)
                .saveAndFlush(argThat(history -> history.getOperationType() == GatewayPaymentOperation.APPROVE));
        approve("test-merchant-a", id, request("100", pan)).andExpect(status().isServiceUnavailable());
        assertThat(used()).isEqualByComparingTo("100");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.READY);
        assertThat(notifications.count()).isZero();
        reset(histories);
        new TransactionTemplate(transactions).executeWithoutResult(status -> merchants.findById(first.getMerchantId())
                .orElseThrow().suspend());
        destinations.deleteAll();
        approved("test-merchant-a", id);
        assertThat(notifications.count()).isEqualTo(1);
        verify(financial, times(1)).approveGatewayCard(any());
    }

    @Test
    void suspensionAndMissingRegisteredTargetsPreventNewFinancialExecution() throws Exception {
        String id = prepare("test-merchant-a", "PAY-SUSPENDED", "100", PaymentMethod.CREDIT_CARD);
        destinations.delete(destinations.findByMerchantMerchantIdAndTargetKey(first.getMerchantId(), "a").orElseThrow());
        approve("test-merchant-a", id, request("100", pan)).andExpect(status().isBadRequest());
        new TransactionTemplate(transactions).executeWithoutResult(status -> merchants.findById(first.getMerchantId())
                .orElseThrow().suspend());
        approve("test-merchant-a", id, request("100", pan)).andExpect(status().isForbidden());
        assertThat(used()).isEqualByComparingTo("0");
        verify(financial, never()).approveGatewayCard(any());
    }

    @Test
    void expiredUnapprovedPgPaymentDoesNotChargeTheCard() throws Exception {
        String id = historical(false);
        approve("test-merchant-a", id, request("100", pan)).andExpect(status().isBadRequest());
        assertThat(used()).isEqualByComparingTo("0");
    }

    @Test
    void anExistingFinancialApprovalCanBeRecoveredAfterThePgDeadline() throws Exception {
        String id = historical(true);
        approved("test-merchant-a", id);
        assertThat(used()).isEqualByComparingTo("100");
        verify(financial, never()).approveGatewayCard(any());
    }

    @Test
    void concurrentDifferentCardsForOnePgIdCannotBothBeCharged() throws Exception {
        String id = prepare("test-merchant-a", "PAY-CARD-RACE", "100", PaymentMethod.CREDIT_CARD);
        CyclicBarrier beforeApprove = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            beforeApprove.await(10, TimeUnit.SECONDS);
            return result;
        }).when(financial).gatewayCardStatus(id);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = pool.submit(() -> approve("test-merchant-a", id, request("100", pan))
                    .andReturn().getResponse().getStatus());
            Future<Integer> b = pool.submit(() -> approve("test-merchant-a", id, request("100", anotherPan))
                    .andReturn().getResponse().getStatus());
            assertThat(List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally { pool.shutdownNow(); }
        assertThat(cards.findAll().stream().map(DummyCard::getCurrentMonthUsedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("100");
        assertThat(approvals.count()).isEqualTo(1);
    }

    @Test
    void failedPgCompensationHistoryRollsBackPgCancellationAndNotificationTogether() throws Exception {
        String id = prepare("test-merchant-a", "PAY-COMPENSATE-FAIL", "100", PaymentMethod.CREDIT_CARD);
        approved("test-merchant-a", id);
        mode.set("REJECTED");
        doThrow(new DataIntegrityViolationException("simulated compensation history failure")).when(histories)
                .saveAndFlush(argThat(history -> history.getOperationType() == GatewayPaymentOperation.CANCEL_APPROVAL));
        dispatcher.retryPending();
        assertThat(used()).isEqualByComparingTo("0");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        assertThat(event(id).getStatus()).isEqualTo("PENDING");
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(2);
        reset(histories);
        retryNow(id);
        dispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.CANCELLED);
        assertThat(event(id).getStatus()).isEqualTo("DELIVERED");
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
        assertThat(used()).isEqualByComparingTo("0");
    }

    @Test
    void malformedFinancialResultsDoNotBecomePgSuccess() throws Exception {
        String id = prepare("test-merchant-a", "PAY-WRONG-REPLY", "100", PaymentMethod.CREDIT_CARD);
        doReturn(Optional.of(new FinancialCardApprovalResponse(id, "buyer", CardCompany.SHINHAN,
                CardCredentials.identity(CardCompany.SHINHAN, pan), "CARD-WRONG", "4111-****-****-1111",
                new BigDecimal("200"), LocalDateTime.now(), CardPaymentHistoryStatus.APPROVED)))
                .when(financial).gatewayCardStatus(id);
        approve("test-merchant-a", id, request("100", pan)).andExpect(status().isServiceUnavailable());
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.READY);
        assertThat(notifications.count()).isZero();
    }

    @Test
    void resultQueryIsScopedAndCredentialsAreRedactedFromStringRepresentations() throws Exception {
        String id = prepare("test-merchant-a", "PAY-QUERY", "100", PaymentMethod.CREDIT_CARD);
        approved("test-merchant-a", id);
        mvc.perform(get("/api/v1/payments/card/gateway/" + id).header("X-Service-Token", "test-merchant-a"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pgStatus").value("PAID"));
        mvc.perform(get("/api/v1/payments/card/gateway/" + id).header("X-Service-Token", "test-merchant-b"))
                .andExpect(status().isNotFound());
        assertThat(request("100", pan).toString()).doesNotContain(pan, "123", "12");
        assertThat(new FinancialCardApproveRequest(id, "buyer", CardCompany.SHINHAN, pan, "123", "12",
                new BigDecimal("100"), deadline).toString()).doesNotContain(pan, "123", "12");
    }

    String historical(boolean financiallyApproved) {
        return new TransactionTemplate(transactions).execute(status -> {
            GatewayPaymentTerms terms = new GatewayPaymentTerms("HISTORICAL", "buyer", PaymentMethod.CREDIT_CARD,
                    new BigDecimal("100"), LocalDateTime.now().minusDays(1).withNano(0), null);
            GatewayPayment payment = payments.saveAndFlush(GatewayPayment.prepare(first, terms));
            histories.saveAndFlush(GatewayPaymentHistory.registered(payment));
            if (financiallyApproved) {
                DummyCard card = cards.findByIdForUpdate(ledger.getDummyCardId()).orElseThrow();
                card.approve(terms.amount());
                approvals.saveAndFlush(DummyCardPaymentHistory.approved(payment.getPgPaymentId(), card, "buyer",
                        payment.getPgPaymentId(), "CARD-HISTORICAL", "4111-****-****-1111", terms.amount()));
            }
            return payment.getPgPaymentId();
        });
    }

    String prepare(String token, String number, String amount, PaymentMethod method) throws Exception {
        GatewayPaymentPrepareRequest request = new GatewayPaymentPrepareRequest(null, number, "buyer", method,
                new BigDecimal(amount), deadline, method == PaymentMethod.BANK_TRANSFER ? BankCompany.KB : null);
        return json.readTree(mvc.perform(post("/api/v1/payments/gateway/prepare").header("X-Service-Token", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray()).get("pgPaymentId").asText();
    }

    JsonNode approved(String token, String id) throws Exception {
        return json.readTree(approve(token, id, request("100", pan)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
    }

    ResultActions approve(String token, String id, GatewayCardApprovalRequest request) throws Exception {
        return mvc.perform(post("/api/v1/payments/card/gateway/" + id + "/approve").header("X-Service-Token", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)));
    }

    GatewayCardApprovalRequest request(String amount, String number) {
        return new GatewayCardApprovalRequest(null, "buyer", CardCompany.SHINHAN, number, "123", "12", new BigDecimal(amount));
    }

    Merchant merchant(String credential, String target) {
        Merchant merchant = Merchant.register("동일한 가맹점명", credential, CardPaymentNotification.CONTRACT, target);
        merchant.activate();
        return merchants.saveAndFlush(merchant);
    }

    DummyCard card(String number, String limit) {
        return cards.saveAndFlush(DummyCard.builder().userId("card-owner").cardCompany(CardCompany.SHINHAN)
                .cardNumberHash(CardCredentials.hash(number)).cardNumberLast4(number.substring(12))
                .cvcHash(encoder.encode("123")).cardPasswordHash(encoder.encode("12")).customerName("카드 소유자")
                .issuedAt(LocalDate.of(2020, 1, 1)).expiresAt(LocalDate.of(2099, 1, 1))
                .limitAmount(new BigDecimal(limit)).build());
    }

    BigDecimal used() { return cards.findById(ledger.getDummyCardId()).orElseThrow().getCurrentMonthUsedAmount(); }
    GatewayCardNotification event(String id) { return notifications.findByPaymentPgPaymentId(id).orElseThrow(); }
    void retryNow(String id) {
        jdbc.update("update gateway_card_notification_outbox set next_attempt_at = ? where pg_payment_id = ?",
                LocalDateTime.now().minusMinutes(1), id);
    }
}
