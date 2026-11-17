package dev.bum.payment_gateway_service.service.gateway.virtualAccount;

import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.paymentgateway.payment.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.exception.*;
import dev.bum.payment_gateway_service.feign.ticket.TicketPaymentClient;
import dev.bum.payment_gateway_service.jpa.gateway.*;
import dev.bum.payment_gateway_service.jpa.merchant.*;
import dev.bum.payment_gateway_service.jpa.virtualAccount.*;
import dev.bum.payment_gateway_service.security.*;
import dev.bum.payment_gateway_service.security.merchant.*;
import dev.bum.payment_gateway_service.service.financial.virtualAccount.*;
import dev.bum.payment_gateway_service.service.gateway.client.FinancialSimulationGatewayService;
import dev.bum.payment_gateway_service.service.gateway.payment.*;
import dev.bum.payment_gateway_service.service.gateway.refund.GatewayRefundProcessService;
import dev.bum.payment_gateway_service.controller.gateway.payment.GatewayPaymentMethodController;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=GatewayBankMethodChangeIntegrationTest.Config.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.open-in-view=false","spring.sql.init.mode=never"})
class GatewayBankMethodChangeIntegrationTest {

    @Configuration
    @Import({GatewayVirtualAccountIssuanceIntegrationTest.Config.class,GatewayPaymentMethodController.class,
            GatewayBankMethodChangeCommands.class,GatewayBankMethodChangeService.class})
    static class Config { }

    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        String url=System.getenv("CARD_METHOD_TEST_POSTGRES_URL");
        if(url!=null&&!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:\\d+/card_method_test"))throw new IllegalArgumentException("격리 DB만 사용합니다.");
        properties.add("spring.datasource.url",()->url==null?"jdbc:h2:mem:bank-method;MODE=PostgreSQL;DB_CLOSE_DELAY=-1":url);
        properties.add("spring.datasource.driver-class-name",()->url==null?"org.h2.Driver":"org.postgresql.Driver");
        properties.add("spring.datasource.username",()->url==null?"sa":"postgres");
        properties.add("spring.datasource.password",()->"");
    }

    @Autowired GatewayBankMethodChangeService changes;
    @Autowired BankVirtualAccountIssueCommandService issuance;
    @Autowired BankAccountIssueGateAccess gateAccess;
    @Autowired BankAccountIssueGateJpaRepository gates;
    @Autowired GatewayPaymentJpaRepository payments;
    @Autowired MerchantJpaRepository merchants;
    @MockitoSpyBean GatewayPaymentHistoryJpaRepository histories;
    @MockitoSpyBean FinancialSimulationGatewayService financial;
    @Autowired DummyVirtualAccountJpaRepository accounts;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean GatewayPaymentMethodChangeService cardChanges;
    @MockitoBean GatewayRefundProcessService refunds;
    @MockitoBean TicketPaymentClient ticket;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenStateStore tokens;
    String id;
    Long merchantId;
    LocalDateTime bankDeadline=LocalDateTime.of(2099,1,2,23,59);
    LocalDateTime cardDeadline=LocalDateTime.of(2099,1,1,12,0);

    @BeforeEach void setup() {
        reset(financial,histories);
        Merchant merchant=Merchant.register("ticksy",null,"CARD_NOTIFICATION_V1","default");merchant.activate();
        merchantId=merchants.saveAndFlush(merchant).getMerchantId();
        id=payments.saveAndFlush(GatewayPayment.prepare(merchant,new GatewayPaymentTerms("PAY-BANK","buyer",PaymentMethod.BANK_TRANSFER,new BigDecimal("100"),bankDeadline,BankCompany.KB))).getPgPaymentId();
    }

    AuthenticatedMerchant principal(){return new AuthenticatedMerchant(merchantId);}
    GatewayBankMethodChangeRequest request(){return new GatewayBankMethodChangeRequest(merchantId,"buyer",new BigDecimal("100"),cardDeadline);}
    BankVirtualAccountIssueRequest bankRequest(){return new BankVirtualAccountIssueRequest(id,BankCompany.KB,new BigDecimal("100"),bankDeadline);}

    @Test void unissuedTransactionSwitchesOnceAndRejectsLateIssuance() {
        GatewayBankMethodChangeResponse result=changes.change(principal(),id,request());
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(result.pgPaymentId()).isEqualTo(id);
        assertThat(result.methodRevision()).isEqualTo(1);
        assertThat(changes.change(principal(),id,request())).isEqualTo(result);
        verify(financial,times(1)).closeGatewayAccountIssue(any());
        assertThat(gates.findById(id).orElseThrow().isClosed()).isTrue();
        assertThatThrownBy(()->issuance.issue(bankRequest())).isInstanceOf(GatewayPaymentConflictException.class);
        assertThat(accounts.findByPgPaymentId(id)).isEmpty();
    }

    @Test void anyPreviouslyIssuedAccountRejectsSwitchEvenWhenPgNeverReceivedTheIssueResponse() {
        BankVirtualAccountIssueResponse issued=issuance.issue(bankRequest());
        assertThatThrownBy(()->changes.change(principal(),id,request())).isInstanceOf(GatewayPaymentConflictException.class);
        assertThat(payments.findById(id).orElseThrow().getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(issuance.issue(bankRequest()).accountNumber()).isEqualTo(issued.accountNumber());
        assertThat(gates.findById(id).orElseThrow().isClosed()).isFalse();
    }

    @Test void cancelledPreviouslyIssuedAccountAlsoRejectsSwitch() {
        issuance.issue(bankRequest());
        new TransactionTemplate(transactions).executeWithoutResult(tx->{DummyVirtualAccount account=accounts.findByPgPaymentId(id).orElseThrow();account.cancel("test");});
        assertThatThrownBy(()->changes.change(principal(),id,request())).isInstanceOf(GatewayPaymentConflictException.class);
    }

    @Test void lostBankClosureResponseKeepsBankMethodAndRetryRecoversClosure() {
        doAnswer(inv->{issuance.closeIssue(inv.getArgument(0));throw new FinancialProviderResponseException();}).when(financial).closeGatewayAccountIssue(any());
        assertThatThrownBy(()->changes.change(principal(),id,request())).isInstanceOf(FinancialProviderResponseException.class);
        assertThat(payments.findById(id).orElseThrow().getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(gates.findById(id).orElseThrow().isClosed()).isTrue();
        doCallRealMethod().when(financial).closeGatewayAccountIssue(any());
        assertThat(changes.change(principal(),id,request()).methodRevision()).isEqualTo(1);
    }

    @Test void pgCommitFailureRollsBackMethodButKeepsBankClosedForSafeRetry() {
        doThrow(new IllegalStateException("save failed")).when(histories).saveAndFlush(argThat(h->h!=null&&"TO_CARD_COMPLETED".equals(h.getOperationKey())));
        assertThatThrownBy(()->changes.change(principal(),id,request())).isInstanceOf(IllegalStateException.class);
        assertThat(payments.findById(id).orElseThrow().getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        assertThat(gates.findById(id).orElseThrow().isClosed()).isTrue();
        reset(histories);
        assertThat(changes.change(principal(),id,request()).methodRevision()).isEqualTo(1);
    }

    @Test void differentMerchantAmountOrDeadlineCannotReplayTheSameOperation() {
        changes.change(principal(),id,request());
        assertThatThrownBy(()->changes.change(new AuthenticatedMerchant(merchantId+10000),id,request())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->changes.change(principal(),id,new GatewayBankMethodChangeRequest(merchantId,"buyer",new BigDecimal("101"),cardDeadline))).isInstanceOf(GatewayPaymentConflictException.class);
        assertThatThrownBy(()->changes.change(principal(),id,new GatewayBankMethodChangeRequest(merchantId,"buyer",new BigDecimal("100"),cardDeadline.plusMinutes(1)))).isInstanceOf(GatewayPaymentConflictException.class);
    }

    @Test void issuanceHoldingTheGateWinsAndPreventsSwitch() throws Exception {
        issuance.closeIssue(new BankVirtualAccountIssueRequest(id,BankCompany.KB,new BigDecimal("100"),bankDeadline));
        // Use a fresh transaction because CLOSED must never be reopened.
        id=payments.saveAndFlush(GatewayPayment.prepare(merchants.findById(merchantId).orElseThrow(),new GatewayPaymentTerms("PAY-RACE","buyer",PaymentMethod.BANK_TRANSFER,new BigDecimal("100"),bankDeadline,BankCompany.KB))).getPgPaymentId();
        BankVirtualAccountIssueRequest issue=bankRequest();
        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> first=pool.submit(()->new TransactionTemplate(transactions).executeWithoutResult(tx->{gateAccess.lock(issue);locked.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException e){throw new RuntimeException(e);}accounts.saveAndFlush(DummyVirtualAccount.issue(issue.pgPaymentId(),issue.pgPaymentId(),issue.bankCompany(),"RACE-"+merchantId,issue.amount(),issue.expiresAt()));}));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            Future<?> second=pool.submit(()->changes.change(principal(),id,request()));
            release.countDown();first.get(15,TimeUnit.SECONDS);
            assertThatThrownBy(()->second.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(GatewayPaymentConflictException.class);
            assertThat(payments.findById(id).orElseThrow().getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
        } finally {release.countDown();pool.shutdownNow();}
    }

    @Test void concurrentFirstIssueAndClosureNeverBothSucceed() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Boolean> issue=pool.submit(()->{start.await();try{issuance.issue(bankRequest());return true;}catch(GatewayPaymentConflictException e){return false;}});
            Future<BankAccountIssueGateResponse> close=pool.submit(()->{start.await();return issuance.closeIssue(bankRequest());});
            start.countDown();boolean issued=issue.get(20,TimeUnit.SECONDS);BankAccountIssueGateResponse closed=close.get(20,TimeUnit.SECONDS);
            assertThat(issued).isNotEqualTo(closed.closed());
            assertThat(accounts.findByPgPaymentId(id).isPresent()).isEqualTo(issued);
            if(closed.closed())assertThatThrownBy(()->issuance.issue(bankRequest())).isInstanceOf(GatewayPaymentConflictException.class);
        } finally {start.countDown();pool.shutdownNow();}
    }
}
