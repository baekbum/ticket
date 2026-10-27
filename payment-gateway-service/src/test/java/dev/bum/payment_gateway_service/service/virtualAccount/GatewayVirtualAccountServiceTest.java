package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.GatewayVirtualAccountStatus;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueResponse;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountRefundResponse;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefund;
import dev.bum.payment_gateway_service.jpa.refund.GatewayRefundJpaRepository;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountOutboxEvent;
import dev.bum.payment_gateway_service.jpa.outbox.VirtualAccountOutboxEventJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistory;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistoryJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentHistoryType;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
import dev.bum.payment_gateway_service.service.refund.GatewayRefundProcessService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;



import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class GatewayVirtualAccountServiceTest {

    @Mock
    private GatewayRefundProcessService refundProcesses;

    @BeforeEach
    void prepareRefundRecord() {
        lenient().when(refundProcesses.prepare(any(GatewayVirtualAccountRefundRequest.class)))
                .thenAnswer(invocation -> GatewayRefund.requested(invocation.getArgument(0, GatewayVirtualAccountRefundRequest.class)));
    }

    @Mock
    private DummyVirtualAccountJpaRepository dummyVirtualAccountJpaRepository;

    @Mock
    private DummyVirtualAccountPaymentHistoryJpaRepository dummyVirtualAccountPaymentHistoryJpaRepository;

    @Mock
    private VirtualAccountDepositOutboxService depositOutboxService;

    @Mock
    private VirtualAccountOutboxEventJpaRepository expirationOutbox;

    @Mock
    private GatewayRefundJpaRepository refunds;

    @InjectMocks
    private GatewayVirtualAccountService gatewayVirtualAccountService;

    @Test
    @DisplayName("무통장 가상계좌를 발급하고 발급 이력을 저장한다")
    void issue_virtual_account() {
        GatewayVirtualAccountIssueRequest request = issueRequest(LocalDate.now().plusDays(2).atTime(18, 0));

        given(dummyVirtualAccountJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.empty());
        given(dummyVirtualAccountJpaRepository.existsByAccountNumber(any())).willReturn(false);
        given(dummyVirtualAccountJpaRepository.save(any(DummyVirtualAccount.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        GatewayVirtualAccountIssueResponse response = gatewayVirtualAccountService.issue(request);

        assertThat(response.getIssued()).isTrue();
        assertThat(response.getStatus()).isEqualTo(GatewayVirtualAccountStatus.WAITING_DEPOSIT);
        assertThat(response.getBankCompany()).isEqualTo(BankCompany.KB);
        assertThat(response.getBankName()).isEqualTo("KB국민은행");
        assertThat(response.getAccountNumber()).startsWith("1111-");
        assertThat(response.getExpiresAt()).isEqualTo(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(23, 59, 59)));

        ArgumentCaptor<DummyVirtualAccount> accountCaptor = ArgumentCaptor.forClass(DummyVirtualAccount.class);
        then(dummyVirtualAccountJpaRepository).should().save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getStatus()).isEqualTo(VirtualAccountPaymentStatus.WAITING_DEPOSIT);
        then(dummyVirtualAccountPaymentHistoryJpaRepository).should().save(any(DummyVirtualAccountPaymentHistory.class));
    }

    @Test
    @DisplayName("이미 발급된 paymentNo는 기존 가상계좌를 반환한다")
    void return_existing_virtual_account() {
        GatewayVirtualAccountIssueRequest request = issueRequest(LocalDate.now().plusDays(2).atTime(18, 0));
        DummyVirtualAccount virtualAccount = DummyVirtualAccount.issue(
                request.getPaymentNo(),
                BankCompany.KB,
                "1111-1234-123456",
                request.getAmount(),
                LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(23, 59, 59))
        );

        given(dummyVirtualAccountJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(virtualAccount));

        GatewayVirtualAccountIssueResponse response = gatewayVirtualAccountService.issue(request);

        assertThat(response.getAccountNumber()).isEqualTo("1111-1234-123456");
        assertThat(response.getStatus()).isEqualTo(GatewayVirtualAccountStatus.WAITING_DEPOSIT);
        assertThat(response.getMessage()).isEqualTo("이미 발급된 가상계좌입니다.");
        then(dummyVirtualAccountJpaRepository).should().findByPaymentNoForUpdate(request.getPaymentNo());
        then(dummyVirtualAccountJpaRepository).shouldHaveNoMoreInteractions();
        then(dummyVirtualAccountPaymentHistoryJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 발급된 paymentNo의 은행이 다르면 재발급을 거부한다")
    void reject_existing_virtual_account_with_different_bank() {
        GatewayVirtualAccountIssueRequest request = issueRequest(LocalDate.now().plusDays(2).atTime(18, 0));
        request.setBankCompany(BankCompany.SHINHAN);
        DummyVirtualAccount virtualAccount = DummyVirtualAccount.issue(
                request.getPaymentNo(),
                BankCompany.KB,
                "1111-1234-123456",
                request.getAmount(),
                LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(23, 59, 59))
        );

        given(dummyVirtualAccountJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(virtualAccount));

        assertThatThrownBy(() -> gatewayVirtualAccountService.issue(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("동일한 결제번호로 다른 가상계좌 발급 정보를 요청할 수 없습니다.");

        then(dummyVirtualAccountPaymentHistoryJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 발급된 paymentNo의 금액이 다르면 재발급을 거부한다")
    void reject_existing_virtual_account_with_different_amount() {
        GatewayVirtualAccountIssueRequest request = issueRequest(LocalDate.now().plusDays(2).atTime(18, 0));
        DummyVirtualAccount virtualAccount = DummyVirtualAccount.issue(
                request.getPaymentNo(),
                BankCompany.KB,
                "1111-1234-123456",
                BigDecimal.valueOf(170000),
                LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(23, 59, 59))
        );

        given(dummyVirtualAccountJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(virtualAccount));

        assertThatThrownBy(() -> gatewayVirtualAccountService.issue(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("동일한 결제번호로 다른 가상계좌 발급 정보를 요청할 수 없습니다.");

        then(dummyVirtualAccountPaymentHistoryJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미 만료된 가상계좌를 조회하면 만료 상태를 반환한다")
    void return_expired_status_for_existing_virtual_account() {
        GatewayVirtualAccountIssueRequest request = issueRequest(LocalDate.now().plusDays(2).atTime(18, 0));
        DummyVirtualAccount virtualAccount = DummyVirtualAccount.issue(
                request.getPaymentNo(),
                BankCompany.KB,
                "1111-1234-123456",
                request.getAmount(),
                LocalDateTime.of(LocalDate.now().minusDays(1), LocalTime.of(23, 59, 59))
        );

        given(dummyVirtualAccountJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.of(virtualAccount));

        GatewayVirtualAccountIssueResponse response = gatewayVirtualAccountService.issue(request);

        assertThat(response.getStatus()).isEqualTo(GatewayVirtualAccountStatus.EXPIRED);
        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountPaymentStatus.EXPIRED);
        assertThat(response.getIssued()).isTrue();
        then(dummyVirtualAccountPaymentHistoryJpaRepository).should().save(any(DummyVirtualAccountPaymentHistory.class));
        then(expirationOutbox).should().save(any(VirtualAccountOutboxEvent.class));
    }

    @Test
    @DisplayName("입금 상태와 입금 이력을 저장하고 Outbox 생성을 위임한다")
    void deposit_virtual_account() {
        DummyVirtualAccount account = virtualAccount();
        GatewayVirtualAccountDepositRequest request = depositRequest(BigDecimal.valueOf(180000));
        given(dummyVirtualAccountJpaRepository.findByAccountNumber(request.getAccountNumber()))
                .willReturn(Optional.of(account));

        GatewayVirtualAccountDepositResponse response = gatewayVirtualAccountService.deposit(request);

        assertThat(response.getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
        assertThat(response.getDepositedAt()).isEqualTo(request.getDepositedAt());
        then(dummyVirtualAccountPaymentHistoryJpaRepository).should().save(any(DummyVirtualAccountPaymentHistory.class));
        then(depositOutboxService).should().enqueue(account);
    }

    @Test
    @DisplayName("동일 입금 재요청은 기존 결과만 반환하고 이력과 알림을 추가하지 않는다")
    void duplicate_deposit_reuses_outbox() {
        DummyVirtualAccount account = completedVirtualAccount();
        GatewayVirtualAccountDepositRequest request = depositRequest(BigDecimal.valueOf(180000));
        given(dummyVirtualAccountJpaRepository.findByAccountNumber(request.getAccountNumber()))
                .willReturn(Optional.of(account));

        GatewayVirtualAccountDepositResponse response = gatewayVirtualAccountService.deposit(request);

        assertThat(response.getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
        then(dummyVirtualAccountPaymentHistoryJpaRepository).shouldHaveNoInteractions();
        then(depositOutboxService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("기존 입금과 입금자나 시각이 다른 재요청은 거부한다")
    void reject_changed_deposit() {
        DummyVirtualAccount account = completedVirtualAccount();
        GatewayVirtualAccountDepositRequest request = depositRequest(BigDecimal.valueOf(180000));
        request.setDepositorName("다른 입금자");
        given(dummyVirtualAccountJpaRepository.findByAccountNumber(request.getAccountNumber()))
                .willReturn(Optional.of(account));

        assertThatThrownBy(() -> gatewayVirtualAccountService.deposit(request))
                .hasMessage("이미 처리된 입금 정보와 요청이 일치하지 않습니다.");
        then(depositOutboxService).shouldHaveNoInteractions();
        then(dummyVirtualAccountPaymentHistoryJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("무통장 전액 환불 이력을 저장하고 환불 완료 응답을 반환한다")
    void refund_virtual_account() {
        DummyVirtualAccount virtualAccount = completedVirtualAccount();
        GatewayVirtualAccountRefundRequest request = refundRequest(BigDecimal.valueOf(180000));

        given(dummyVirtualAccountJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo()))
                .willReturn(Optional.of(virtualAccount));

        GatewayVirtualAccountRefundResponse response = gatewayVirtualAccountService.refund(request);

        assertThat(response.getPaymentNo()).isEqualTo("PAY-20260727120000-abcdef123456");
        assertThat(response.getRefundBankCompany()).isEqualTo(BankCompany.KB);
        assertThat(response.getRefundAccountNumber()).isEqualTo("123-456-7890");
        assertThat(response.getRefundAccountHolder()).isEqualTo("홍길동");
        assertThat(response.getRefundedAmount()).isEqualByComparingTo("180000");
        assertThat(response.getMessage()).isEqualTo("무통장 환불 입금이 완료되었습니다.");
        assertRefundHistorySaved(virtualAccount, request);
        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
    }

    @Test
    @DisplayName("무통장 부분 환불 이력을 저장하고 환불 완료 응답을 반환한다")
    void refund_partial_virtual_account() {
        DummyVirtualAccount virtualAccount = completedVirtualAccount();
        GatewayVirtualAccountRefundRequest request = refundRequest(BigDecimal.valueOf(90000));

        given(dummyVirtualAccountJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo()))
                .willReturn(Optional.of(virtualAccount));

        GatewayVirtualAccountRefundResponse response = gatewayVirtualAccountService.refund(request);

        assertThat(response.getPaymentNo()).isEqualTo("PAY-20260727120000-abcdef123456");
        assertThat(response.getRefundedAmount()).isEqualByComparingTo("90000");
        assertThat(response.getMessage()).isEqualTo("무통장 환불 입금이 완료되었습니다.");
        assertRefundHistorySaved(virtualAccount, request);
        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
    }

    @Test
    @DisplayName("결제 금액을 초과한 환불 요청은 성공 이력을 저장하지 않는다")
    void reject_refund_amount_exceeding_payment() {
        DummyVirtualAccount virtualAccount = completedVirtualAccount();
        GatewayVirtualAccountRefundRequest request = refundRequest(BigDecimal.valueOf(180001));
        given(dummyVirtualAccountJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo()))
                .willReturn(Optional.of(virtualAccount));

        assertThatThrownBy(() -> gatewayVirtualAccountService.refund(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("환불 금액이 결제 금액을 초과했습니다.");
        then(dummyVirtualAccountPaymentHistoryJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("환불 이력 저장에 실패하면 성공 응답 대신 오류를 전달한다")
    void propagate_refund_history_save_failure() {
        DummyVirtualAccount virtualAccount = completedVirtualAccount();
        GatewayVirtualAccountRefundRequest request = refundRequest(BigDecimal.valueOf(90000));
        given(dummyVirtualAccountJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo()))
                .willReturn(Optional.of(virtualAccount));
        given(dummyVirtualAccountPaymentHistoryJpaRepository.save(any(DummyVirtualAccountPaymentHistory.class)))
                .willThrow(new IllegalStateException("이력 저장 실패"));

        assertThatThrownBy(() -> gatewayVirtualAccountService.refund(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("이력 저장 실패");
        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
    }

    @Test
    @DisplayName("입금 완료 반영 전 가상계좌는 환불할 수 없다")
    void reject_refund_not_completed_virtual_account() {
        DummyVirtualAccount virtualAccount = virtualAccount();
        GatewayVirtualAccountRefundRequest request = refundRequest(BigDecimal.valueOf(180000));

        given(dummyVirtualAccountJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo()))
                .willReturn(Optional.of(virtualAccount));

        assertThatThrownBy(() -> gatewayVirtualAccountService.refund(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("환불할 수 없는 가상계좌 상태입니다.");
        then(dummyVirtualAccountPaymentHistoryJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("입금 금액이 다르면 입금 상태로 변경하지 않는다")
    void reject_deposit_amount_mismatch() {
        DummyVirtualAccount virtualAccount = virtualAccount();
        GatewayVirtualAccountDepositRequest request = depositRequest(BigDecimal.valueOf(170000));

        given(dummyVirtualAccountJpaRepository.findByAccountNumber(request.getAccountNumber()))
                .willReturn(Optional.of(virtualAccount));

        assertThatThrownBy(() -> gatewayVirtualAccountService.deposit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("입금 금액이 일치하지 않습니다.");

        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountPaymentStatus.WAITING_DEPOSIT);
        then(dummyVirtualAccountPaymentHistoryJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("입금 기한이 지나면 만료 상태로 변경한다")
    void expire_deposit_after_expires_at() {
        DummyVirtualAccount virtualAccount = virtualAccount();
        GatewayVirtualAccountDepositRequest request = GatewayVirtualAccountDepositRequest.builder()
                .accountNumber("1111-1234-123456")
                .depositorName("아이유")
                .amount(BigDecimal.valueOf(180000))
                .depositedAt(LocalDateTime.of(2099, 7, 28, 0, 0))
                .build();

        given(dummyVirtualAccountJpaRepository.findByAccountNumber(request.getAccountNumber()))
                .willReturn(Optional.of(virtualAccount));

        assertThatThrownBy(() -> gatewayVirtualAccountService.deposit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("입금 기한이 만료되었습니다.");

        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountPaymentStatus.EXPIRED);
        then(dummyVirtualAccountPaymentHistoryJpaRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("공연 당일에는 무통장 가상계좌를 발급할 수 없다")
    void reject_virtual_account_on_event_day() {
        GatewayVirtualAccountIssueRequest request = issueRequest(LocalDate.now().atTime(18, 0));

        given(dummyVirtualAccountJpaRepository.findByPaymentNoForUpdate(request.getPaymentNo())).willReturn(Optional.empty());

        assertThatThrownBy(() -> gatewayVirtualAccountService.issue(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("공연 당일에는 무통장 입금을 사용할 수 없습니다.");
    }

    private GatewayVirtualAccountIssueRequest issueRequest(LocalDateTime eventDateTime) {
        return GatewayVirtualAccountIssueRequest.builder()
                .paymentNo("PAY-20260727120000-abcdef123456")
                .bankCompany(BankCompany.KB)
                .amount(BigDecimal.valueOf(180000))
                .eventDateTime(eventDateTime)
                .build();
    }

    private GatewayVirtualAccountDepositRequest depositRequest(BigDecimal amount) {
        return GatewayVirtualAccountDepositRequest.builder()
                .accountNumber("1111-1234-123456")
                .depositorName("아이유")
                .amount(amount)
                .depositedAt(LocalDateTime.of(2026, 8, 19, 12, 0))
                .build();
    }

    private void assertRefundHistorySaved(DummyVirtualAccount virtualAccount, GatewayVirtualAccountRefundRequest request) {
        ArgumentCaptor<DummyVirtualAccountPaymentHistory> captor = ArgumentCaptor.forClass(DummyVirtualAccountPaymentHistory.class);
        then(dummyVirtualAccountPaymentHistoryJpaRepository).should().save(captor.capture());
        DummyVirtualAccountPaymentHistory history = captor.getValue();
        assertThat(history.getVirtualAccount()).isSameAs(virtualAccount);
        assertThat(history.getPaymentNo()).isEqualTo(request.getPaymentNo());
        assertThat(history.getHistoryType()).isEqualTo(VirtualAccountPaymentHistoryType.REFUNDED);
        assertThat(history.getMessage()).contains(
                request.getRefundBankCompany().name(), request.getRefundAccountNumber(),
                request.getRefundAccountHolder(), request.getRefundAmount().toPlainString() + "원"
        );
    }

    private GatewayVirtualAccountRefundRequest refundRequest(BigDecimal amount) {
        return GatewayVirtualAccountRefundRequest.builder()
                .paymentRefundProcessId(1L)
                .paymentNo("PAY-20260727120000-abcdef123456")
                .refundBankCompany(BankCompany.KB)
                .refundAccountNumber("123-456-7890")
                .refundAccountHolder("홍길동")
                .refundAmount(amount)
                .build();
    }

    private DummyVirtualAccount virtualAccount() {
        return DummyVirtualAccount.issue(
                "PAY-20260727120000-abcdef123456",
                BankCompany.KB,
                "1111-1234-123456",
                BigDecimal.valueOf(180000),
                LocalDateTime.of(2099, 7, 27, 23, 59, 59)
        );
    }

    private DummyVirtualAccount completedVirtualAccount() {
        DummyVirtualAccount virtualAccount = virtualAccount();
        virtualAccount.deposit("아이유", LocalDateTime.of(2026, 8, 19, 12, 0));

        return virtualAccount;
    }

}
