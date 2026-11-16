package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.controller.gateway.refund.GatewayPaymentRefundController;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import dev.bum.payment_gateway_service.security.*;
import dev.bum.payment_gateway_service.security.merchant.*;
import dev.bum.payment_gateway_service.service.gateway.refund.*;
import dev.bum.payment_gateway_service.service.gateway.notification.*;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = GatewayBankRefundIntegrationTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:gateway-bank-refunds;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.jpa.open-in-view=false"})
@AutoConfigureMockMvc
class GatewayBankRefundIntegrationTest {

    static class Faults {
        AtomicInteger drop = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger unreadable = new AtomicInteger();
    }

    @Configuration
    @Import({GatewayVirtualAccountDepositIntegrationTest.Config.class, GatewayPaymentRefundController.class,
            GatewayBankRefundService.class, GatewayBankRefundCommands.class, GatewayBankRefundClient.class, GatewayPaymentRefundService.class, GatewayCardRefundService.class, GatewayCardRefundCommands.class, GatewayCardRefundClient.class})
    static class Config {
        @Bean Faults faults() { return new Faults(); }
        @Bean @Primary RestClient refundTestRestClient(FinancialSimulationToken token, Faults faults) {
            return RestClient.builder().defaultHeader(FinancialSimulationAuthenticationFilter.TOKEN_HEADER, token.value())
                    .requestInterceptor((request, body, next) -> {
                        boolean refund = request.getURI().getPath().contains("/gateway/refunds/");
                        if (refund) faults.calls.incrementAndGet();
                        ClientHttpResponse response = next.execute(request, body);
                        if (refund && faults.drop.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                            response.close();
                            throw new ResourceAccessException("simulated committed response loss");
                        }
                        if (refund && faults.unreadable.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                            response.close();
                            throw new RestClientException("simulated unreadable committed response");
                        }
                        return response;
                    }).build();
        }
    }

    @Autowired GatewayBankRefundService service;
    @Autowired BankVirtualAccountRefundService bank;
    @Autowired GatewayPaymentJpaRepository payments;
    @Autowired MerchantJpaRepository merchants;
    @Autowired DummyVirtualAccountJpaRepository accounts;
    @Autowired GatewayBankRefundJpaRepository refunds;
    @MockitoSpyBean BankVirtualAccountRefundJpaRepository bankRefunds;
    @Autowired DummyVirtualAccountPaymentHistoryJpaRepository bankHistories;
    @MockitoSpyBean GatewayPaymentHistoryJpaRepository histories;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Faults faults;
    @Autowired MockMvc mvc;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenStateStore tokens;
    @MockitoBean GatewayVirtualAccountService legacy;
    @MockitoBean VirtualAccountCancellationService legacyCancel;
    @MockitoBean MerchantNotificationTargetResolver targets;
    @MockitoBean StandardBankNotificationAdapter notifications;
    Merchant first;
    Merchant second;
    String paymentId;
    final LocalDateTime deadline = LocalDateTime.of(2099, 1, 1, 12, 0);

    @BeforeEach void setup() {
        reset(histories, bankRefunds);
        faults.unreadable.set(0);
        faults.drop.set(0); faults.calls.set(0);
        bankRefunds.deleteAll(); refunds.deleteAll(); bankHistories.deleteAll(); histories.deleteAll();
        accounts.deleteAll(); payments.deleteAll(); merchants.deleteAll();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            first = merchant("ticksy_a"); second = merchant("melon_b");
            paymentId = payment(first, "SAME-PAYMENT");
        });
    }

    Merchant merchant(String key) {
        Merchant row = Merchant.register("같은 이름", MerchantCredentialKey.hash(key), "PAYMENT_NOTIFICATION_V1", "default");
        row.activate(); return merchants.saveAndFlush(row);
    }

    String payment(Merchant merchant, String number) {
        GatewayPayment payment = payments.saveAndFlush(GatewayPayment.prepare(merchant,
                new GatewayPaymentTerms(number, "buyer", PaymentMethod.BANK_TRANSFER, new BigDecimal("100"), deadline, BankCompany.KB)));
        payment.waitForDeposit(); payment.receiveDeposit(LocalDateTime.of(2026,11,13,12,0));
        DummyVirtualAccount account = DummyVirtualAccount.issue(payment.getPgPaymentId(), payment.getPgPaymentId(), BankCompany.KB,
                "ACC-" + payment.getPgPaymentId().substring(8, 16), payment.getAmount(), deadline);
        account.deposit("입금자", LocalDateTime.of(2026,11,13,12,0)); accounts.saveAndFlush(account);
        return payment.getPgPaymentId();
    }

    GatewayBankRefundRequest request(String amount) {
        return new GatewayBankRefundRequest(first.getMerchantId(), "buyer", new BigDecimal(amount), BankCompany.KB, "1234567890", "환불자");
    }

    BankRefundResult call(String key, String amount, boolean withdraw) {
        return service.execute(new AuthenticatedMerchant(first.getMerchantId()), paymentId, key, request(amount), withdraw);
    }


    @Test void anUnreadableBankResponseIsRetriedUsingTheSameReceipt() {
        faults.unreadable.set(3);
        assertThat(call("1", "40", false).outcome()).isEqualTo("SUCCEEDED");
        assertThat(faults.calls.get()).isEqualTo(4);
        assertThat(bankRefunds.count()).isEqualTo(1);
        assertThat(bankHistories.count()).isEqualTo(1);
    }

    @Test void bankReceiptStorageFailureRollsBackTheBalanceAndBankHistoryTogether() {
        doThrow(new DataIntegrityViolationException("simulated bank receipt storage failure"))
                .when(bankRefunds).saveAndFlush(any(BankVirtualAccountRefund.class));
        assertThatThrownBy(() -> bank.refund(request("40").toBank(paymentId, "1")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(accounts.findByPgPaymentId(paymentId).orElseThrow().getRefundedAmount()).isZero();
        assertThat(bankHistories.count()).isZero();
        assertThat(bankRefunds.count()).isZero();
    }

    @Test void partialAndRemainingFullRefundsAreAppliedOnceAndPreserveTheOriginalDeposit() {
        BankRefundResult first = call("1", "40", false);
        assertThat(call("1", "40.00", false)).isEqualTo(first);
        assertThat(call("1", "40", true)).isEqualTo(first);
        assertThat(faults.calls.get()).isEqualTo(1);
        call("2", "60", false);
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.REFUNDED);
        DummyVirtualAccount account = accounts.findByPgPaymentId(paymentId).orElseThrow();
        assertThat(account.getRefundedAmount()).isEqualByComparingTo("100");
        assertThat(account.getDepositedAt()).isEqualTo(LocalDateTime.of(2026,11,13,12,0));
        assertThat(bankHistories.count()).isEqualTo(2);
    }

    @Test void changedAmountOrDestinationOnTheSameKeyIsRejectedWithoutAnotherBankCall() {
        call("1", "40", false);
        assertThatThrownBy(() -> call("1", "41", false)).isInstanceOf(RuntimeException.class);
        GatewayBankRefundRequest changed = new GatewayBankRefundRequest(first.getMerchantId(), "buyer", new BigDecimal("40"), BankCompany.KB, "different", "환불자");
        assertThatThrownBy(() -> service.execute(new AuthenticatedMerchant(first.getMerchantId()), paymentId, "1", changed, false))
                .isInstanceOf(RuntimeException.class);
        assertThat(faults.calls.get()).isEqualTo(1);
    }

    @Test void aWithdrawnRequestCannotBeExecutedByALateRefundAndAnotherRequestCanProceed() {
        BankRefundResult closed = call("1", "40", true);
        assertThat(closed.outcome()).isEqualTo("WITHDRAWN");
        assertThat(call("1", "40", false)).isEqualTo(closed);
        assertThat(bank.refund(request("40").toBank(paymentId, "1"))).isEqualTo(closed);
        assertThat(accounts.findByPgPaymentId(paymentId).orElseThrow().getRefundedAmount()).isZero();
        call("2", "50", false);
        assertThat(accounts.findByPgPaymentId(paymentId).orElseThrow().getRefundedAmount()).isEqualByComparingTo("50");
    }

    @Test void threeLostBankResponsesRecoverWithinFourAttemptsWithoutDuplicatingFunds() {
        faults.drop.set(3);
        assertThat(call("1", "40", false).outcome()).isEqualTo("SUCCEEDED");
        assertThat(faults.calls.get()).isEqualTo(4);
        assertThat(bankRefunds.count()).isEqualTo(1);
        assertThat(bankHistories.count()).isEqualTo(1);
    }

    @Test void fourLostResponsesRemainUnconfirmedAndWithdrawalRecoversTheAlreadyCommittedRefund() {
        faults.drop.set(4);
        assertThatThrownBy(() -> call("1", "40", false)).isInstanceOf(RuntimeException.class);
        assertThat(faults.calls.get()).isEqualTo(4);
        assertThat(payments.findById(paymentId).orElseThrow().getStatus()).isEqualTo(GatewayPaymentStatus.PAID);
        assertThat(accounts.findByPgPaymentId(paymentId).orElseThrow().getRefundedAmount()).isEqualByComparingTo("40");
        assertThat(call("1", "40", true).outcome()).isEqualTo("SUCCEEDED");
        assertThat(payments.findById(paymentId).orElseThrow().getRefundedAmount()).isEqualByComparingTo("40");
        assertThat(bankHistories.count()).isEqualTo(1);
    }

    @Test void aDefiniteBankRejectionIsNotRetriedAndDoesNotChangePgFinancialStatus() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> accounts.findByPgPaymentId(paymentId).orElseThrow().cancel("closed"));
        assertThatThrownBy(() -> call("1", "40", false)).isInstanceOf(RuntimeException.class);
        assertThat(faults.calls.get()).isEqualTo(1);
        assertThat(payments.findById(paymentId).orElseThrow().getRefundedAmount()).isZero();
    }

    @Test void pgStorageFailureRollsBackOnlyPgAndTheSameBankReceiptRepairsIt() {
        doThrow(new DataIntegrityViolationException("simulated history failure")).when(histories).saveAndFlush(any());
        assertThatThrownBy(() -> call("1", "40", false)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(payments.findById(paymentId).orElseThrow().getRefundedAmount()).isZero();
        assertThat(refunds.findByPaymentPgPaymentIdAndRefundRequestId(paymentId, "1").orElseThrow().getStatus()).isEqualTo("REQUESTED");
        reset(histories);
        call("1", "40", false);
        assertThat(accounts.findByPgPaymentId(paymentId).orElseThrow().getRefundedAmount()).isEqualByComparingTo("40");
        assertThat(bankHistories.count()).isEqualTo(1);
    }

    @Test void differentMerchantsCanReuseRefundIdsAndPaymentNumbersButCannotAccessEachOther() throws Exception {
        String other = new TransactionTemplate(transactions).execute(tx -> payment(second, "SAME-PAYMENT"));
        call("1", "40", false);
        GatewayBankRefundRequest input = new GatewayBankRefundRequest(second.getMerchantId(), "buyer", new BigDecimal("40"), BankCompany.KB, "1234567890", "환불자");
        service.execute(new AuthenticatedMerchant(second.getMerchantId()), other, "1", input, false);
        assertThat(refunds.count()).isEqualTo(2);
        assertThatThrownBy(() -> service.execute(new AuthenticatedMerchant(second.getMerchantId()), paymentId, "1", input, false))
                .isInstanceOf(RuntimeException.class);
        mvc.perform(post("/api/v1/payments/gateway/" + paymentId + "/refunds/1").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void concurrentBankRefundAndWithdrawalHaveExactlyOneFinancialOutcome() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            BankRefundCommand command = request("40").toBank(paymentId, "1");
            Future<BankRefundResult> a = pool.submit(() -> { start.await(); return bank.refund(command); });
            Future<BankRefundResult> b = pool.submit(() -> { start.await(); return bank.withdraw(command); });
            start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
            assertThat(bankRefunds.count()).isEqualTo(1);
            assertThat(bankHistories.count()).isLessThanOrEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
}
