package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.paymentgateway.notification.*;
import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.controller.payment.GatewayPaymentNotificationController;
import dev.bum.ticket_service.security.*;
import dev.bum.ticket_service.service.payment.notification.*;
import dev.bum.ticket_service.service.payment.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = GatewayPaymentNotificationController.class, properties = {
        "app.internal.service-token=test-pg-inbound", "app.payment-gateway.merchant-id=1"
})
@Import({SecurityConfig.class, InternalServiceTokenValidator.class, GatewayPaymentNotificationService.class,
        GatewayBankNotificationService.class, GatewayCardNotificationService.class})
class GatewayPaymentNotificationControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenStateStore tokens;
    @MockitoBean PaymentService payments;
    final String path = "/api/v1/payments/internal/gateway/notifications";
    final String pgId = "20261109550e8400-e29b-41d4-a716-446655440000";

    @Test
    void bankDepositUsesTheLocalOwnerAwareIdempotentFlowAndReturnsTheCommonAcknowledgement() throws Exception {
        when(payments.completeVirtualAccountDepositFromGateway(any(), eq("buyer"))).thenReturn(payment(PaymentStatus.PAID));
        BankDepositNotification event = event();
        byte[] result = send(json.valueToTree(event), "test-pg-inbound").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("APPLIED"))
                .andExpect(jsonPath("$.pgPaymentId").value(pgId)).andExpect(jsonPath("$.payment").doesNotExist())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(json.readValue(result, BankDepositNotificationResponse.class).matches(event)).isTrue();
        verify(payments).completeVirtualAccountDepositFromGateway(argThat(request -> request.getPaymentNo().equals("PAY-1")
                && request.getBankCompany() == BankCompany.KB && request.getAccountNumber().equals("1111-123456")
                && request.getDepositedAt().equals(event.depositedAt())), eq("buyer"));
    }

    @Test
    void duplicateBankNotificationsEchoTheSameEvent() throws Exception {
        when(payments.completeVirtualAccountDepositFromGateway(any(), eq("buyer"))).thenReturn(payment(PaymentStatus.PAID));
        ObjectNode body = json.valueToTree(event());
        String first = send(body, "test-pg-inbound").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = send(body, "test-pg-inbound").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(first).isEqualTo(second);
    }

    @Test
    void cancelledReservationAcknowledgementDoesNotClaimThatFundsWereReturned() throws Exception {
        when(payments.completeVirtualAccountDepositFromGateway(any(), eq("buyer")))
                .thenReturn(payment(PaymentStatus.CANCELLED));
        send(json.valueToTree(event()), "test-pg-inbound").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("RESERVATION_CANCELLED"))
                .andExpect(jsonPath("$.fundsReturned").doesNotExist());
    }

    @Test
    void theSameRegisteredEndpointAlsoAcceptsExistingCardNotificationFields() throws Exception {
        when(payments.settleCardFromGateway(any())).thenReturn(new CardPaymentSettlementResponse(
                CardPaymentSettlementResponse.Outcome.COMPLETED, PaymentResponse.builder().paymentNo("PAY-1")
                        .method(PaymentMethod.CREDIT_CARD).status(PaymentStatus.PAID).amount(100).cardTransactionId("CARD-1").build()));
        CardPaymentNotification card = new CardPaymentNotification(1, pgId + ":APPROVE:INITIAL", 1L, pgId, "PAY-1", "buyer",
                PaymentMethod.CREDIT_CARD, PaymentStatus.PAID, new BigDecimal("100"), "CARD-1", CardCompany.SHINHAN,
                "4111-****-****-1111", LocalDateTime.of(2026, 11, 9, 12, 0));
        send(json.valueToTree(card), "test-pg-inbound").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"));
        verify(payments, never()).completeVirtualAccountDepositFromGateway(any(), anyString());
    }

    @Test
    void anotherMerchantIsRejectedBeforeLocalPaymentAccess() throws Exception {
        ObjectNode body = json.valueToTree(event());
        body.put("merchantId", 2);
        send(body, "test-pg-inbound").andExpect(status().isForbidden());
        verifyNoInteractions(payments);
    }

    @Test
    void unsignedAndInvalidlySignedNotificationsAreForbidden() throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(event())))
                .andExpect(status().isForbidden());
        send(json.valueToTree(event()), "invalid").andExpect(status().isForbidden());
        verifyNoInteractions(payments);
    }

    @Test
    void invalidVersionsEventsMethodsAmountsAndUnknownTypeNamesAreRejected() throws Exception {
        for (String field : new String[]{"version", "eventId", "paymentMethod", "amount", "depositedAt"}) {
            ObjectNode body = json.valueToTree(event());
            switch (field) {
                case "version" -> body.put(field, 2);
                case "eventId" -> body.put(field, "OTHER");
                case "paymentMethod" -> body.put(field, "com.example.ArbitraryClass");
                case "amount" -> body.put(field, -1);
                case "depositedAt" -> body.putNull(field);
            }
            send(body, "test-pg-inbound").andExpect(status().isBadRequest());
        }
        verifyNoInteractions(payments);
    }

    @Test
    void anUnclearLocalResultCannotBecomeASuccessAcknowledgement() {
        when(payments.completeVirtualAccountDepositFromGateway(any(), eq("buyer")))
                .thenReturn(payment(PaymentStatus.WAITING_DEPOSIT));
        assertThatThrownBy(() -> new GatewayBankNotificationService(payments, 1).receive(event()))
                .isInstanceOf(IllegalStateException.class);
    }

    BankDepositNotification event() {
        return new BankDepositNotification(1, pgId + ":DEPOSIT:INITIAL", 1L, pgId, "PAY-1", "buyer",
                PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID, new BigDecimal("100"), "1111-123456", BankCompany.KB,
                "입금자", LocalDateTime.of(2026, 11, 9, 12, 0));
    }

    PaymentResponse payment(PaymentStatus status) {
        return PaymentResponse.builder().paymentNo("PAY-1").method(PaymentMethod.BANK_TRANSFER).status(status).amount(100)
                .bankName(BankCompany.KB.getBankName()).accountNumber("1111-123456").depositorName("입금자")
                .paidAt("2026-11-09 12:00:00").build();
    }

    ResultActions send(ObjectNode body, String token) throws Exception {
        return mvc.perform(post(path).header("X-Service-Token", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }
}
