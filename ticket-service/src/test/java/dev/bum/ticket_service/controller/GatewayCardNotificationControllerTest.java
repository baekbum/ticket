package dev.bum.ticket_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenStateStore;
import dev.bum.common.service.paymentgateway.notification.*;
import dev.bum.common.service.ticket.payment.dto.*;
import dev.bum.common.service.ticket.payment.enums.*;
import dev.bum.ticket_service.controller.payment.GatewayCardNotificationController;
import dev.bum.ticket_service.security.InternalServiceTokenValidator;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.payment.notification.CardPaymentNotificationService;
import dev.bum.ticket_service.service.payment.payment.PaymentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = GatewayCardNotificationController.class, properties = {
        "app.internal.service-token=test-pg-inbound", "app.payment-gateway.merchant-id=1"
})
@Import({SecurityConfig.class, InternalServiceTokenValidator.class, CardPaymentNotificationService.class})
class GatewayCardNotificationControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenStateStore tokenStateStore;
    @MockitoBean PaymentService payments;

    final String path = "/api/v1/payments/internal/gateway/card/notifications";
    final String pgId = "20261109550e8400-e29b-41d4-a716-446655440000";

    @Test
    void standardNotificationMapsToLocalCardSettlementAndReturnsOnlyTheCommonAcknowledgement() throws Exception {
        CardPaymentNotification event = event();
        doReturn(result(CardPaymentSettlementResponse.Outcome.COMPLETED, PaymentStatus.PAID))
                .when(payments).settleCardFromGateway(any(), anyString());
        byte[] response = send(event, "test-pg-inbound").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ACCEPTED"))
                .andExpect(jsonPath("$.pgPaymentId").value(pgId))
                .andExpect(jsonPath("$.merchantPaymentNo").value("PAY-1"))
                .andExpect(jsonPath("$.payment").doesNotExist())
                .andReturn().getResponse().getContentAsByteArray();
        CardPaymentNotificationResponse ack = json.readValue(response, CardPaymentNotificationResponse.class);
        assertThat(ack.matches(event)).isTrue();
        verify(payments).settleCardFromGateway(argThat(request -> request.getPaymentNo().equals("PAY-1")
                && request.getUserId().equals("buyer") && request.getTransactionId().equals("CARD-1")
                && request.getAmount().compareTo(new BigDecimal("100")) == 0), eq(pgId));
    }

    @Test
    void definitiveReservationRejectionUsesTheSameCommonResponseContract() throws Exception {
        doReturn(result(CardPaymentSettlementResponse.Outcome.REJECTED, PaymentStatus.CANCELLED))
                .when(payments).settleCardFromGateway(any(), anyString());
        send(event(), "test-pg-inbound").andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REJECTED"))
                .andExpect(jsonPath("$.eventId").value(event().eventId()));
    }

    @Test
    void repeatedDeliveryUsesTheSameLocalTransactionIdentity() throws Exception {
        doReturn(result(CardPaymentSettlementResponse.Outcome.COMPLETED, PaymentStatus.PAID))
                .when(payments).settleCardFromGateway(any(), anyString());
        byte[] first = send(event(), "test-pg-inbound").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        byte[] second = send(event(), "test-pg-inbound").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(json.readTree(first)).isEqualTo(json.readTree(second));
        verify(payments, times(2)).settleCardFromGateway(argThat(request -> request.getTransactionId().equals("CARD-1")), eq(pgId));
    }

    @Test
    void authenticationDoesNotAllowMissingOrInvalidServiceTokens() throws Exception {
        send(event(), "invalid").andExpect(status().isForbidden());
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(event())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(payments);
    }

    @Test
    @WithMockUser(roles = "USER")
    void userAuthenticationDoesNotAuthorizeInternalPaymentNotifications() throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(event())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(payments);
    }

    @Test
    void anotherMerchantsNotificationIsRejectedBeforeLookingUpAPayment() throws Exception {
        ObjectNode body = json.valueToTree(event());
        body.put("merchantId", 2);
        mvc.perform(post(path).header("X-Service-Token", "test-pg-inbound").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body))).andExpect(status().isForbidden());
        verifyNoInteractions(payments);
    }

    @Test
    void invalidVersionsAmountsEventIdentityAndMethodsCannotReachLocalPaymentProcessing() throws Exception {
        for (String field : new String[]{"version", "amount", "eventId", "paymentMethod", "status"}) {
            ObjectNode body = json.valueToTree(event());
            switch (field) {
                case "version" -> body.put(field, 2);
                case "amount" -> body.put(field, -1);
                case "eventId" -> body.put(field, "OTHER-EVENT");
                case "paymentMethod" -> body.put(field, "BANK_TRANSFER");
                case "status" -> body.put(field, "READY");
            }
            mvc.perform(post(path).header("X-Service-Token", "test-pg-inbound").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(payments);
    }

    @Test
    void unclearLocalResultCannotBecomeAnAcceptedOrRejectedAcknowledgement() {
        doReturn(result(CardPaymentSettlementResponse.Outcome.COMPLETED, PaymentStatus.READY))
                .when(payments).settleCardFromGateway(any(), anyString());
        CardPaymentNotificationService receiver = new CardPaymentNotificationService(payments, 1);
        assertThatThrownBy(() -> receiver.receive(event())).isInstanceOf(IllegalStateException.class);
    }

    ResultActions send(CardPaymentNotification event, String token) throws Exception {
        return mvc.perform(post(path).header("X-Service-Token", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(event)));
    }

    CardPaymentNotification event() {
        return new CardPaymentNotification(1, pgId + ":APPROVE:INITIAL", 1L, pgId, "PAY-1", "buyer",
                PaymentMethod.CREDIT_CARD, PaymentStatus.PAID, new BigDecimal("100"), "CARD-1",
                CardCompany.SHINHAN, "4111-****-****-1111", LocalDateTime.of(2026, 11, 9, 12, 0));
    }

    CardPaymentSettlementResponse result(CardPaymentSettlementResponse.Outcome outcome, PaymentStatus status) {
        return new CardPaymentSettlementResponse(outcome, PaymentResponse.builder().paymentNo("PAY-1")
                .method(PaymentMethod.CREDIT_CARD).status(status).amount(100).cardTransactionId("CARD-1").build());
    }
}
