package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.common.service.ticket.payment.enums.BankCompany;
import dev.bum.payment_gateway_service.dto.bank.*;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BankExpirationNotificationClientTest {
    ObjectMapper json = new ObjectMapper().findAndRegisterModules().disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    MockRestServiceServer server;
    BankExpirationNotificationClient client;
    final String id = "20261112550e8400-e29b-41d4-a716-446655440000";
    final BankExpirationEvent event = new BankExpirationEvent(id + ":EXPIRE:INITIAL", id, "1111-123456", BankCompany.KB,
            new BigDecimal("100"), LocalDateTime.of(2026, 11, 12, 18, 0), LocalDateTime.of(2026, 11, 12, 18, 0, 1));

    @BeforeEach void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BankExpirationNotificationClient(builder.build(), new MockEnvironment());
        ReflectionTestUtils.setField(client, "configuredBaseUrl", "http://pg");
    }

    @Test void sendsTheStableFinancialEventAndAcceptsOnlyTheExactAcknowledgement() throws Exception {
        server.expect(once(), requestTo("http://pg/internal/financial-simulations/gateway/bank-expirations"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("X-PG-Event-Id", event.eventId()))
                .andExpect(request -> {
                    byte[] bytes = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsBytes();
                    assertThat(json.readValue(bytes, BankExpirationEvent.class)).isEqualTo(event);
                })
                .andRespond(withSuccess(json.writeValueAsString(BankExpirationAcknowledgement.of(event)), MediaType.APPLICATION_JSON));
        assertThat(client.send(event).matches(event)).isTrue();
        server.verify();
    }

    @Test void anotherOriginalExpirationTimestampCannotCompleteDelivery() throws Exception {
        BankExpirationEvent other = new BankExpirationEvent(event.eventId(), id, event.accountNumber(), event.bankCompany(),
                event.amount(), event.expiresAt(), event.expiredAt().plusSeconds(1));
        server.expect(requestTo("http://pg/internal/financial-simulations/gateway/bank-expirations"))
                .andRespond(withSuccess(json.writeValueAsString(BankExpirationAcknowledgement.of(other)), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.send(event)).isInstanceOf(IllegalStateException.class);
    }

    @Test void anAcceptedButUncommitted202IsNotADurablePgAcknowledgement() throws Exception {
        server.expect(requestTo("http://pg/internal/financial-simulations/gateway/bank-expirations"))
                .andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                        .body(json.writeValueAsString(BankExpirationAcknowledgement.of(event))));
        assertThatThrownBy(() -> client.send(event)).isInstanceOf(IllegalStateException.class);
    }
}
