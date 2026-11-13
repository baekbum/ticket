package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import dev.bum.payment_gateway_service.security.merchant.MerchantCredentialKey;

import com.fasterxml.jackson.databind.*;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.paymentgateway.notification.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.dto.gateway.*;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.outbox.*;
import dev.bum.payment_gateway_service.service.outbox.BankTransferOutboxCommands;
import org.springframework.jdbc.core.JdbcTemplate;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import dev.bum.payment_gateway_service.security.merchant.AuthenticatedMerchant;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.*;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import dev.bum.payment_gateway_service.service.gateway.notification.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = GatewayVirtualAccountDepositIntegrationTest.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:gateway-cancel;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false", "spring.sql.init.mode=never",
        "app.financial-simulation.base-url=", "app.financial-simulation.token=", "management.tracing.enabled=false",
        "app.ticket.service-token=merchant-a", "app.payment-provider.token=bank-provider"
})
@AutoConfigureMockMvc
class GatewayVirtualAccountCancellationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MerchantJpaRepository merchants;
    @Autowired MerchantNotificationEndpointJpaRepository endpoints;
    @Autowired GatewayPaymentJpaRepository payments;
    @Autowired DummyVirtualAccountJpaRepository accounts;
    @Autowired GatewayBankNotificationJpaRepository notifications;
    @Autowired GatewayVirtualAccountCancellationService cancellation;
    @Autowired BankVirtualAccountCancellationService bankCancellation;
    @Autowired BankVirtualAccountDepositService bankDeposits;
    @Autowired BankDepositOutboxDispatcher bankDispatcher;
    @Autowired GatewayBankCancellationDispatcher cancelDispatcher;
    @Autowired GatewayBankNotificationDispatcher dispatcher;
    @Autowired BankTransferOutboxCommands deliveryCommands;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean BankTransferOutboxJpaRepository outboxes;
    @MockitoSpyBean BankVirtualAccountCancellationJpaRepository receipts;
    @MockitoSpyBean FinancialSimulationGatewayService financial;
    @MockitoSpyBean GatewayPaymentHistoryJpaRepository histories;
    @MockitoSpyBean DummyVirtualAccountPaymentHistoryJpaRepository bankHistories;
    @MockitoBean GatewayVirtualAccountService legacy;
    @MockitoBean VirtualAccountCancellationService legacyCancellation;
    @MockitoBean MerchantNotificationTargetResolver targets;
    @MockitoBean StandardBankNotificationAdapter adapter;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenStateStore tokens;

    final LocalDateTime deadline = LocalDateTime.of(2099, 1, 1, 23, 59, 59);
    final LocalDateTime depositedAt = LocalDateTime.of(2026, 11, 10, 12, 0, 0, 123456000);
    Merchant first;
    Merchant second;

    @BeforeEach
    void setup() {
        outboxes.deleteAll();
        receipts.deleteAll();
        notifications.deleteAll();
        bankHistories.deleteAll();
        accounts.deleteAll();
        histories.deleteAll();
        payments.deleteAll();
        endpoints.deleteAll();
        merchants.deleteAll();
        first = merchant("ticksy");
        second = merchant("melon");
        when(targets.resolve(anyLong(), anyString(), anyString())).thenReturn(
                new MerchantNotificationTarget(URI.create("https://registered.example/hook"), "notify-token"));
        when(adapter.send(any(), anyString(), any())).thenReturn(BankDepositNotificationResponse.Outcome.RESERVATION_CANCELLED);
        when(adapter.sendCancellation(any(), anyString(), any())).thenAnswer(invocation ->
                BankCancellationNotificationResponse.of(invocation.getArgument(2)));
    }

    @Test
    void acceptanceDoesNotCallTheBankOrClaimCancellationSuccess() throws Exception {
        String id = issue("merchant-a", "ACCEPTED");
        clearInvocations(financial);
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.cancelledAt").doesNotExist());
        verifyNoInteractions(financial);
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.WAITING_DEPOSIT);
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        assertThat(event(id, BankTransferOutbox.Flow.BANK_CANCEL_REQUEST).getStatus()).isEqualTo("PENDING");
        cancelDispatcher.retryPending();
        assertThat(event(id, BankTransferOutbox.Flow.BANK_CANCEL_REQUEST).getStatus()).isEqualTo("DELIVERED");
        assertThat(event(id, BankTransferOutbox.Flow.MERCHANT_CANCEL_RESULT).getStatus()).isEqualTo("DELIVERED");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.CANCELLED);
        assertThat(receipts.count()).isEqualTo(1);
        assertThat(notifications.count()).isZero();
        verify(adapter).sendCancellation(eq(URI.create("https://registered.example/hook")), eq("notify-token"),
                argThat(e -> e.pgPaymentId().equals(id) && e.outcome().equals("CANCELLED") && !e.fundsReturned()));
    }

    @Test
    void duplicateAcceptanceAndFinalReplayKeepOneOperationAndTheFirstTimestamp() throws Exception {
        String id = issue("merchant-a", "REPLAY");
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        assertThat(outboxes.count()).isEqualTo(1);
        cancelDispatcher.retryPending();
        String a = cancel("merchant-a", id, request(id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var equivalent = new GatewayVirtualAccountPaymentCancelRequest(first.getMerchantId(), "buyer", new BigDecimal("100.0000"),
                BankCompany.KB, request(id).accountNumber());
        String b = cancel("merchant-a", id, equivalent).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(a).isEqualTo(b);
        assertThat(outboxes.count()).isEqualTo(2);
        assertThat(bankHistories.count()).isEqualTo(2);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(3);
        verify(financial, times(1)).requestGatewayCancellation(any());
    }

    @Test
    void theSameMerchantPaymentNumberIsIndependentAcrossMerchants() throws Exception {
        String a = issue("merchant-a", "SAME-NO"), b = issue("merchant-b", "SAME-NO");
        cancel("merchant-a", a, request(a)).andExpect(status().isAccepted());
        cancel("merchant-b", b, request(b)).andExpect(status().isAccepted());
        cancelDispatcher.retryPending();
        assertThat(receipts.count()).isEqualTo(2);
        assertThat(outboxes.count()).isEqualTo(4);
        verify(adapter).sendCancellation(any(), anyString(), argThat(e -> e.pgPaymentId().equals(a) && e.merchantId().equals(first.getMerchantId())));
        verify(adapter).sendCancellation(any(), anyString(), argThat(e -> e.pgPaymentId().equals(b) && e.merchantId().equals(second.getMerchantId())));
    }

    @Test
    void otherMerchantAndMissingTransactionsReturn404WithoutEnqueueing() throws Exception {
        String id = issue("merchant-a", "OWNER");
        cancel("merchant-b", id, request(id)).andExpect(status().isNotFound());
        cancel("merchant-b", UUID.randomUUID().toString(), request(id)).andExpect(status().isNotFound());
        mvc.perform(get(statusPath(id)).header("Merchant", second.getMerchantId()).header("credentialKey", "merchant-b")).andExpect(status().isNotFound());
        assertThat(outboxes.count()).isZero();
    }

    @Test
    void spoofedMerchantAndWrongBuyerAmountBankOrAccountAreRejected() throws Exception {
        String id = issue("merchant-a", "BAD-TERMS");
        var r = request(id);
        cancel("merchant-a", id, new GatewayVirtualAccountPaymentCancelRequest(second.getMerchantId(), "buyer", r.amount(), r.bankCompany(), r.accountNumber()))
                .andExpect(status().isForbidden());
        for (var bad : List.of(
                new GatewayVirtualAccountPaymentCancelRequest(null, "other", r.amount(), r.bankCompany(), r.accountNumber()),
                new GatewayVirtualAccountPaymentCancelRequest(null, "buyer", new BigDecimal("101"), r.bankCompany(), r.accountNumber()),
                new GatewayVirtualAccountPaymentCancelRequest(null, "buyer", r.amount(), BankCompany.SHINHAN, r.accountNumber()),
                new GatewayVirtualAccountPaymentCancelRequest(null, "buyer", r.amount(), r.bankCompany(), "wrong-account"))) {
            cancel("merchant-a", id, bad).andExpect(status().isConflict());
        }
        assertThat(outboxes.count()).isZero();
    }

    @Test
    void changedTermsAfterAcceptanceAndCompletionAreStillRejected() throws Exception {
        String id = issue("merchant-a", "CHANGED");
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        var bad = new GatewayVirtualAccountPaymentCancelRequest(null, "buyer", new BigDecimal("101"), BankCompany.KB, request(id).accountNumber());
        cancel("merchant-a", id, bad).andExpect(status().isConflict());
        cancelDispatcher.retryPending();
        cancel("merchant-a", id, bad).andExpect(status().isConflict());
    }

    @Test
    void authenticationAndStoredMethodAreEnforced() throws Exception {
        String id = issue("merchant-a", "AUTH");
        mvc.perform(post(path(id)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request(id))))
                .andExpect(status().isUnauthorized());
        cancel("bank-provider", id, request(id)).andExpect(status().isUnauthorized());
        String card = prepare("merchant-a", "CARD", PaymentMethod.CREDIT_CARD);
        cancel("merchant-a", card, request(id)).andExpect(status().isConflict());
        mvc.perform(post("/internal/financial-simulations/banks/virtual-accounts/gateway/cancel")
                .header("Merchant", first.getMerchantId()).header("credentialKey", "merchant-a").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(bankRequest(id))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspendedMerchantMayCleanUpAnExistingTransaction() throws Exception {
        String id = issue("merchant-a", "SUSPENDED");
        new TransactionTemplate(transactions).executeWithoutResult(s -> merchants.findById(first.getMerchantId()).orElseThrow().suspend());
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        cancelDispatcher.retryPending();
        cancel("merchant-a", id, request(id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void bankDecidesPaidCancellationAndTheRejectionIsDeliveredWithoutChangingPg() throws Exception {
        String id = issue("merchant-a", "BANK-REJECTS");
        bankDeposits.deposit(depositRequest(id));
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        cancelDispatcher.retryPending();
        assertThat(event(id, BankTransferOutbox.Flow.BANK_CANCEL_REQUEST).getStatus()).isEqualTo("DELIVERED");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        assertThat(notifications.count()).isZero();
        cancel("merchant-a", id, request(id)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("ALREADY_DEPOSITED"));
        verify(adapter).sendCancellation(any(), anyString(), argThat(e -> e.outcome().equals("REJECTED") && e.cancelledAt() == null));
        bankDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
    }

    @Test
    void alreadyPaidPgStillAsksTheBankInsteadOfDecidingLocally() throws Exception {
        String id = issue("merchant-a", "PG-PAID");
        bankDeposits.deposit(depositRequest(id));
        bankDispatcher.retryPending();
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        cancelDispatcher.retryPending();
        verify(financial).requestGatewayCancellation(bankRequest(id));
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        assertThat(payments.findById(id).orElseThrow().getPaidAt()).isEqualTo(depositedAt);
    }

    @Test
    void unissuedAccountProducesADurableBankRejectionRatherThanEndlessRetry() throws Exception {
        String id = prepare("merchant-a", "NOT-ISSUED", PaymentMethod.BANK_TRANSFER);
        var request = new GatewayVirtualAccountPaymentCancelRequest(null, "buyer", new BigDecimal("100"), BankCompany.KB, "not-issued");
        cancel("merchant-a", id, request).andExpect(status().isAccepted());
        cancelDispatcher.retryPending();
        assertThat(event(id, BankTransferOutbox.Flow.BANK_CANCEL_REQUEST).getStatus()).isEqualTo("DELIVERED");
        cancel("merchant-a", id, request).andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void lostIssueConfirmationDoesNotPreventBankCancellation() throws Exception {
        String id = prepare("merchant-a", "ISSUE-LOSS", PaymentMethod.BANK_TRANSFER);
        financial.issueGatewayAccount(new BankVirtualAccountIssueRequest(id, BankCompany.KB, new BigDecimal("100"), deadline));
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        cancelDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.CANCELLED);
        assertThat(notifications.count()).isZero();
    }

    @Test
    void bankHistoryFailureRollsBackTheBankAndLeavesRequestRetryable() throws Exception {
        String id = issue("merchant-a", "BANK-HISTORY-FAIL");
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        doThrow(new DataIntegrityViolationException("simulated")).when(bankHistories)
                .saveAndFlush(argThat(h -> h.getHistoryType() == VirtualAccountPaymentHistoryType.CANCELLED));
        cancelDispatcher.retryPending();
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.WAITING_DEPOSIT);
        assertThat(receipts.count()).isZero();
        assertThat(event(id, BankTransferOutbox.Flow.BANK_CANCEL_REQUEST).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void bankReceiptFailureAlsoRollsBackTheBankStateAndHistory() throws Exception {
        String id = issue("merchant-a", "BANK-RECEIPT-FAIL");
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        doThrow(new DataIntegrityViolationException("simulated")).when(receipts).saveAndFlush(any());
        cancelDispatcher.retryPending();
        assertThat(accounts.findByPgPaymentId(id).orElseThrow().getStatus()).isEqualTo(VirtualAccountPaymentStatus.WAITING_DEPOSIT);
        assertThat(bankHistories.count()).isEqualTo(1);
    }

    @Test
    void lostBankResponseRetriesTheSameReceiptWithoutCancellingTwice() throws Exception {
        String id = issue("merchant-a", "BANK-RESPONSE-LOSS");
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        doAnswer(i -> { i.callRealMethod(); throw new ResourceAccessException("lost response"); })
                .when(financial).requestGatewayCancellation(any());
        cancelDispatcher.retryPending();
        assertThat(receipts.count()).isEqualTo(1);
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        reset(financial);
        retryNow(id);
        cancelDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.CANCELLED);
        assertThat(bankHistories.count()).isEqualTo(2);
        assertThat(receipts.count()).isEqualTo(1);
    }

    @Test
    void pgHistoryFailureRollsBackPgResultAndNextOutboxTogether() throws Exception {
        String id = issue("merchant-a", "PG-HISTORY-FAIL");
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        doThrow(new DataIntegrityViolationException("simulated")).when(histories)
                .saveAndFlush(argThat(h -> h.getOperationType() == GatewayPaymentOperation.CANCEL));
        cancelDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        assertThat(outboxes.findByFlowAndPgPaymentId(BankTransferOutbox.Flow.MERCHANT_CANCEL_RESULT,id)).isEmpty();
        assertThat(event(id,BankTransferOutbox.Flow.BANK_CANCEL_REQUEST).getStatus()).isEqualTo("PENDING");
        reset(histories);
        retryNow(id);
        cancelDispatcher.retryPending();
        assertThat(bankHistories.count()).isEqualTo(2);
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.CANCELLED);
    }

    @Test
    void nextOutboxFailureRollsBackPgAndDeliveredFlagAndCanBeRetried() throws Exception {
        String id = issue("merchant-a", "NEXT-OUTBOX-FAIL");
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        doThrow(new DataIntegrityViolationException("simulated")).when(outboxes)
                .saveAndFlush(argThat(e -> e.getFlow() == BankTransferOutbox.Flow.MERCHANT_CANCEL_RESULT));
        cancelDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
        assertThat(histories.countByPaymentPgPaymentId(id)).isEqualTo(2);
        reset(outboxes);
        retryNow(id);
        cancelDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.CANCELLED);
        assertThat(receipts.count()).isEqualTo(1);
    }

    @Test
    void merchantFailureRetriesOnlyTheResultWithTheSameEventAndContent() throws Exception {
        String id = issue("merchant-a", "MERCHANT-RETRY");
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        doThrow(new ResourceAccessException("unavailable")).when(adapter).sendCancellation(any(),anyString(),any());
        cancelDispatcher.retryPending();
        assertThat(event(id,BankTransferOutbox.Flow.BANK_CANCEL_REQUEST).getStatus()).isEqualTo("DELIVERED");
        assertThat(event(id,BankTransferOutbox.Flow.MERCHANT_CANCEL_RESULT).getStatus()).isEqualTo("PENDING");
        Map<String,Object> payload = event(id,BankTransferOutbox.Flow.MERCHANT_CANCEL_RESULT).getPayload();
        reset(adapter);
        when(adapter.sendCancellation(any(),anyString(),any())).thenAnswer(i -> BankCancellationNotificationResponse.of(i.getArgument(2)));
        retryNow(id);
        cancelDispatcher.retryPending();
        assertThat(event(id,BankTransferOutbox.Flow.MERCHANT_CANCEL_RESULT).getPayload()).isEqualTo(payload);
        assertThat(event(id,BankTransferOutbox.Flow.MERCHANT_CANCEL_RESULT).getStatus()).isEqualTo("DELIVERED");
        verify(financial,times(1)).requestGatewayCancellation(any());
    }

    @Test
    void aDifferentBankResultCannotCompleteTheRequest() throws Exception {
        String id = issue("merchant-a", "WRONG-BANK");
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        doAnswer(i -> { var r=(BankVirtualAccountCancellationResult)i.callRealMethod();
            return new BankVirtualAccountCancellationResult("other:CANCEL:INITIAL",r.request(),r.outcome(),r.reason(),r.account());
        }).when(financial).requestGatewayCancellation(any());
        cancelDispatcher.retryPending();
        assertThat(event(id,BankTransferOutbox.Flow.BANK_CANCEL_REQUEST).getStatus()).isEqualTo("PENDING");
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.WAITING_DEPOSIT);
    }

    @Test
    void bankDuplicateRejectsChangedAmountOrAccountAndKeepsTheFirstRejection() throws Exception {
        String id = issue("merchant-a", "BANK-IDEMPOTENCY");
        bankDeposits.deposit(depositRequest(id));
        var firstResult=bankCancellation.cancelCommand(bankRequest(id));
        assertThat(firstResult.outcome()).isEqualTo("REJECTED");
        bankDeposits.rejectDeposit(id);
        assertThat(bankCancellation.cancelCommand(bankRequest(id))).isEqualTo(firstResult);
        assertThatThrownBy(() -> bankCancellation.cancelCommand(new BankVirtualAccountCancelRequest(id,BankCompany.KB,
                request(id).accountNumber(),new BigDecimal("101")))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void cancellationOfAnAlreadyCancelledDepositDoesNotExecuteDepositProcessing() throws Exception {
        String id = issue("merchant-a", "NO-DEPOSIT-COUPLING");
        bankDeposits.deposit(depositRequest(id));
        bankDeposits.rejectDeposit(id);
        cancel("merchant-a",id,request(id)).andExpect(status().isAccepted());
        cancelDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.CANCELLED);
        assertThat(payments.findById(id).orElseThrow().getPaidAt()).isNull();
        assertThat(notifications.count()).isZero();
        assertThat(histories.findByPaymentPgPaymentIdAndOperationTypeAndOperationKey(id,GatewayPaymentOperation.CANCEL,"INITIAL")
                .orElseThrow().getDetails()).containsEntry("depositedAt",depositedAt.toString()).containsEntry("fundsReturned",false);
        bankDispatcher.retryPending();
        assertThat(payments.findById(id).orElseThrow().getPaidAt()).isEqualTo(depositedAt);
        assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.CANCELLED);
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void cancellationWinsBeforeDepositAndTheBankRejectsTheDeposit() throws Exception {
        String id = issue("merchant-a", "CANCEL-WINS");
        cancel("merchant-a",id,request(id)).andExpect(status().isAccepted());
        cancelDispatcher.retryPending();
        assertThatThrownBy(() -> bankDeposits.deposit(depositRequest(id))).isInstanceOf(RuntimeException.class);
        assertThat(outboxes.findByFlowAndPgPaymentId(BankTransferOutbox.Flow.BANK_DEPOSIT,id)).isEmpty();
    }

    @Test
    void concurrentAcceptancesCreateOneRequest() throws Exception {
        String id = issue("merchant-a", "CONCURRENT");
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Integer>a=pool.submit(() -> cancel("merchant-a",id,request(id)).andReturn().getResponse().getStatus());
            Future<Integer>b=pool.submit(() -> cancel("merchant-a",id,request(id)).andReturn().getResponse().getStatus());
            assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsOnly(202);
        } finally { pool.shutdownNow(); }
        assertThat(outboxes.count()).isEqualTo(1);
        cancelDispatcher.retryPending();
        assertThat(receipts.count()).isEqualTo(1);
    }

    @Test
    void anExpiredLeaseCanBeReclaimedAndAnOldWorkerCannotOverwriteCompletion() throws Exception {
        String id=issue("merchant-a","LEASE");
        cancel("merchant-a",id,request(id)).andExpect(status().isAccepted());
        var e=event(id,BankTransferOutbox.Flow.BANK_CANCEL_REQUEST);
        var old=deliveryCommands.claim(e.getOutboxId(),e.getFlow());
        assertThat(deliveryCommands.claim(e.getOutboxId(),e.getFlow())).isNull();
        jdbc.update("update bank_transfer_outbox set lease_until=? where outbox_id=?",LocalDateTime.now().minusSeconds(1),e.getOutboxId());
        cancelDispatcher.retryPending();
        deliveryCommands.failed(old,"late response");
        assertThat(event(id,e.getFlow()).getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void statusQueryIsMerchantScopedAndReturnsTheDurableResult() throws Exception {
        String id=issue("merchant-a","QUERY");
        cancel("merchant-a",id,request(id)).andExpect(status().isAccepted());
        mvc.perform(get(statusPath(id)).header("Merchant", first.getMerchantId()).header("credentialKey", "merchant-a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
        cancelDispatcher.retryPending();
        mvc.perform(get(statusPath(id)).header("Merchant", first.getMerchantId()).header("credentialKey", "merchant-a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get(statusPath(id)).header("Merchant", second.getMerchantId()).header("credentialKey", "merchant-b")).andExpect(status().isNotFound());
    }

    @Test
    void concurrentBankDepositAndCancellationConvergeWithoutLosingTheFirstDeposit() throws Exception {
        String id = issue("merchant-a", "BANK-RACE");
        cancel("merchant-a", id, request(id)).andExpect(status().isAccepted());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> deposit = pool.submit(() -> {
                start.await();
                try { bankDeposits.deposit(depositRequest(id)); return true; }
                catch (dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException rejected) { return false; }
            });
            Future<?> cancel = pool.submit(() -> { start.await(); cancelDispatcher.retryPending(); return null; });
            start.countDown();
            boolean deposited = deposit.get(30, TimeUnit.SECONDS);
            cancel.get(30, TimeUnit.SECONDS);
            bankDispatcher.retryPending();
            if (deposited) {
                assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
                assertThat(payments.findById(id).orElseThrow().getPaidAt()).isEqualTo(depositedAt);
                assertThat(event(id, BankTransferOutbox.Flow.BANK_CANCEL_REQUEST).getResult()).containsEntry("outcome", "REJECTED");
            } else {
                assertThat(payments.findById(id).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.CANCELLED);
                assertThat(accounts.findByPgPaymentId(id).orElseThrow().getDepositedAt()).isNull();
            }
            assertThat(event(id, BankTransferOutbox.Flow.BANK_CANCEL_REQUEST).getStatus()).isEqualTo("DELIVERED");
            assertThat(receipts.count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void noOuterTransactionMaySpanAnOutboxHttpCall() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> cancelDispatcher.retryPending()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> bankDispatcher.retryPending()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    BankTransferOutbox event(String id,BankTransferOutbox.Flow flow) { return outboxes.findByFlowAndPgPaymentId(flow,id).orElseThrow(); }
    void retryNow(String id) { jdbc.update("update bank_transfer_outbox set next_attempt_at=? where pg_payment_id=? and status='PENDING'",LocalDateTime.now().minusSeconds(1),id); }
    String statusPath(String id) { return "/api/v1/payments/virtual-account/gateway/"+id+"/cancellation"; }

    GatewayVirtualAccountPaymentCancelRequest request(String id) {
        return new GatewayVirtualAccountPaymentCancelRequest(null, "buyer", new BigDecimal("100"), BankCompany.KB,
                accounts.findByPgPaymentId(id).orElseThrow().getAccountNumber());
    }

    BankVirtualAccountCancelRequest bankRequest(String id) {
        return new BankVirtualAccountCancelRequest(id, BankCompany.KB, request(id).accountNumber(), new BigDecimal("100"));
    }

    GatewayVirtualAccountDepositRequest depositRequest(String id) {
        return new GatewayVirtualAccountDepositRequest(request(id).accountNumber(), "입금자", new BigDecimal("100"), depositedAt);
    }

    ResultActions deposit(String id) throws Exception {
        return mvc.perform(post("/api/v1/banks/virtual-accounts/deposit").header("X-Payment-Provider-Token", "bank-provider")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(depositRequest(id))));
    }

    ResultActions cancel(String token, String id, GatewayVirtualAccountPaymentCancelRequest request) throws Exception {
        return mvc.perform(post(path(id)).header("Merchant", "merchant-b".equals(token) ? second.getMerchantId() : first.getMerchantId())
                .header("credentialKey", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request)));
    }

    String path(String id) { return "/api/v1/payments/virtual-account/gateway/" + id + "/cancel"; }

    Merchant merchant(String key) {
        Merchant merchant = Merchant.register("동일한 가맹점명", MerchantCredentialKey.hash("ticksy".equals(key) ? "merchant-a" : "merchant-b"), GatewayPaymentNotification.CONTRACT, "default");
        merchant.activate();
        return merchants.saveAndFlush(merchant);
    }

    String issue(String token, String number) throws Exception {
        String id = prepare(token, number, PaymentMethod.BANK_TRANSFER);
        mvc.perform(post("/api/v1/payments/virtual-account/gateway/" + id + "/issue").header("Merchant", "merchant-b".equals(token) ? second.getMerchantId() : first.getMerchantId())
                .header("credentialKey", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(
                        new GatewayVirtualAccountPaymentIssueRequest(null, "buyer", new BigDecimal("100"), BankCompany.KB))))
                .andExpect(status().isOk());
        return id;
    }

    String prepare(String token, String number, PaymentMethod method) throws Exception {
        var request = new GatewayPaymentPrepareRequest(null, number, "buyer", method, new BigDecimal("100"), deadline,
                method == PaymentMethod.BANK_TRANSFER ? BankCompany.KB : null);
        return json.readTree(mvc.perform(post("/api/v1/payments/gateway/prepare").header("Merchant", "merchant-b".equals(token) ? second.getMerchantId() : first.getMerchantId())
                .header("credentialKey", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("pgPaymentId").asText();
    }
}
