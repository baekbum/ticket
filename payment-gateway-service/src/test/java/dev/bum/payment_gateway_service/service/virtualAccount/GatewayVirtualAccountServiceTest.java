package dev.bum.payment_gateway_service.service.virtualAccount;

import dev.bum.common.service.ticket.payment.dto.PaymentResponse;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.common.service.ticket.payment.enums.PaymentMethod;
import dev.bum.common.service.ticket.payment.enums.PaymentStatus;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountIssueResponse;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositRequest;
import dev.bum.payment_gateway_service.dto.virtualAccount.GatewayVirtualAccountDepositResponse;
import dev.bum.payment_gateway_service.feign.ticket.TicketPaymentClient;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccount;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistory;
import dev.bum.payment_gateway_service.jpa.virtualAccount.DummyVirtualAccountPaymentHistoryJpaRepository;
import dev.bum.payment_gateway_service.jpa.virtualAccount.VirtualAccountPaymentStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class GatewayVirtualAccountServiceTest {

    @Mock
    private DummyVirtualAccountJpaRepository dummyVirtualAccountJpaRepository;

    @Mock
    private DummyVirtualAccountPaymentHistoryJpaRepository dummyVirtualAccountPaymentHistoryJpaRepository;

    @Mock
    private TicketPaymentClient ticketPaymentClient;

    @InjectMocks
    private GatewayVirtualAccountService gatewayVirtualAccountService;

    @Test
    @DisplayName("무통장 가상계좌를 발급하고 발급 이력을 저장한다")
    void issue_virtual_account() {
        GatewayVirtualAccountIssueRequest request = issueRequest(LocalDate.now().plusDays(2).atTime(18, 0));

        given(dummyVirtualAccountJpaRepository.findByPaymentNo(request.getPaymentNo())).willReturn(Optional.empty());
        given(dummyVirtualAccountJpaRepository.existsByAccountNumber(any())).willReturn(false);
        given(dummyVirtualAccountJpaRepository.save(any(DummyVirtualAccount.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(ticketPaymentClient.applyVirtualAccountIssued(any())).willReturn(paymentResponse());

        GatewayVirtualAccountIssueResponse response = gatewayVirtualAccountService.issue(request);

        assertThat(response.getIssued()).isTrue();
        assertThat(response.getBankCompany()).isEqualTo(BankCompany.KB);
        assertThat(response.getBankName()).isEqualTo("KB국민은행");
        assertThat(response.getAccountNumber()).startsWith("1111-");
        assertThat(response.getExpiresAt()).isEqualTo(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(23, 59, 59)));

        ArgumentCaptor<DummyVirtualAccount> accountCaptor = ArgumentCaptor.forClass(DummyVirtualAccount.class);
        then(dummyVirtualAccountJpaRepository).should().save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getStatus()).isEqualTo(VirtualAccountPaymentStatus.WAITING_DEPOSIT);
        then(dummyVirtualAccountPaymentHistoryJpaRepository).should().save(any(DummyVirtualAccountPaymentHistory.class));
        then(ticketPaymentClient).should().applyVirtualAccountIssued(any());
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

        given(dummyVirtualAccountJpaRepository.findByPaymentNo(request.getPaymentNo())).willReturn(Optional.of(virtualAccount));

        GatewayVirtualAccountIssueResponse response = gatewayVirtualAccountService.issue(request);

        assertThat(response.getAccountNumber()).isEqualTo("1111-1234-123456");
        assertThat(response.getMessage()).isEqualTo("이미 발급된 가상계좌입니다.");
        then(dummyVirtualAccountJpaRepository).should().findByPaymentNo(request.getPaymentNo());
        then(dummyVirtualAccountJpaRepository).shouldHaveNoMoreInteractions();
        then(dummyVirtualAccountPaymentHistoryJpaRepository).shouldHaveNoInteractions();
        then(ticketPaymentClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("가상계좌 입금 요청 시 입금 완료 상태로 변경하고 이력을 저장한다")
    void deposit_virtual_account() {
        DummyVirtualAccount virtualAccount = virtualAccount();
        GatewayVirtualAccountDepositRequest request = depositRequest(BigDecimal.valueOf(180000));

        given(dummyVirtualAccountJpaRepository.findByAccountNumber(request.getAccountNumber()))
                .willReturn(Optional.of(virtualAccount));

        GatewayVirtualAccountDepositResponse response = gatewayVirtualAccountService.deposit(request);

        assertThat(response.getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
        assertThat(response.getDepositorName()).isEqualTo("아이유");
        assertThat(response.getDepositedAt()).isEqualTo(LocalDateTime.of(2026, 8, 19, 12, 0));
        assertThat(virtualAccount.getStatus()).isEqualTo(VirtualAccountPaymentStatus.DEPOSITED);
        then(dummyVirtualAccountPaymentHistoryJpaRepository).should().save(any(DummyVirtualAccountPaymentHistory.class));
        then(ticketPaymentClient).shouldHaveNoInteractions();
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
        then(ticketPaymentClient).shouldHaveNoInteractions();
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
        then(ticketPaymentClient).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("공연 당일에는 무통장 가상계좌를 발급할 수 없다")
    void reject_virtual_account_on_event_day() {
        GatewayVirtualAccountIssueRequest request = issueRequest(LocalDate.now().atTime(18, 0));

        given(dummyVirtualAccountJpaRepository.findByPaymentNo(request.getPaymentNo())).willReturn(Optional.empty());

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

    private DummyVirtualAccount virtualAccount() {
        return DummyVirtualAccount.issue(
                "PAY-20260727120000-abcdef123456",
                BankCompany.KB,
                "1111-1234-123456",
                BigDecimal.valueOf(180000),
                LocalDateTime.of(2099, 7, 27, 23, 59, 59)
        );
    }

    private PaymentResponse paymentResponse() {
        return PaymentResponse.builder()
                .paymentId(1L)
                .reservationId(1L)
                .orderId("ORDER-1")
                .paymentNo("PAY-20260727120000-abcdef123456")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.WAITING_DEPOSIT)
                .amount(180000)
                .bankName("KB국민은행")
                .accountNumber("1111-1234-123456")
                .build();
    }
}
