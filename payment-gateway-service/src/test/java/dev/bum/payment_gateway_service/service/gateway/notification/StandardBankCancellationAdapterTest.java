package dev.bum.payment_gateway_service.service.gateway.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.bum.common.service.paymentgateway.notification.*;
import dev.bum.common.service.ticket.payment.enums.*;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.*;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class StandardBankCancellationAdapterTest {
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final AtomicReference<String> mode = new AtomicReference<>("OK");
    final AtomicReference<String> header = new AtomicReference<>();
    final AtomicReference<BankCancellationNotification> received = new AtomicReference<>();
    HttpServer server;
    URI url;
    StandardBankNotificationAdapter adapter;

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            var event = json.readValue(exchange.getRequestBody(), BankCancellationNotification.class);
            received.set(event);
            header.set(exchange.getRequestHeaders().getFirst("X-PG-Event-Type"));
            var response = BankCancellationNotificationResponse.of(event);
            if (mode.get().equals("WRONG")) {
                response = new BankCancellationNotificationResponse("other-event", response.merchantId(), response.pgPaymentId(),
                        response.merchantPaymentNo(), response.amount(), response.outcome());
            }
            byte[] body = mode.get().equals("EMPTY") ? new byte[0] : json.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(mode.get().equals("ACCEPTED") ? 202 : 200, body.length == 0 ? -1 : body.length);
            if (body.length != 0) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
        adapter = new StandardBankNotificationAdapter(RestClient.builder());
    }

    @AfterEach void stop() { server.stop(0); }

    @Test
    void sendsTheStandardHeaderAndAnExplicitDtoToTheRegisteredUrl() {
        var event = event("CANCELLED");
        assertThat(adapter.sendCancellation(url, "registered-token", event).matches(event)).isTrue();
        assertThat(received.get()).isEqualTo(event);
        assertThat(header.get()).isEqualTo(BankCancellationNotification.EVENT_TYPE);
    }

    @Test
    void businessRejectionIsAlsoAFinalAcknowledgement() {
        var event = event("REJECTED");
        assertThat(adapter.sendCancellation(url, "registered-token", event).outcome()).isEqualTo("REJECTED");
    }

    @Test
    void acceptedEmptyAndWrongEventResponsesAreNotDeliveryCompletion() {
        for (String response : new String[]{"ACCEPTED", "EMPTY", "WRONG"}) {
            mode.set(response);
            assertThatThrownBy(() -> adapter.sendCancellation(url, "registered-token", event("CANCELLED")))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    BankCancellationNotification event(String outcome) {
        String id = "20261110550e8400-e29b-41d4-a716-446655440000";
        return new BankCancellationNotification(1, id + ":CANCEL:INITIAL", 1L, id, "PAY-1", "buyer",
                PaymentMethod.BANK_TRANSFER, new BigDecimal("100"), "1111-123456", BankCompany.KB, outcome,
                outcome.equals("CANCELLED") ? "PG_MERCHANT_CANCELLED" : "ALREADY_DEPOSITED",
                outcome.equals("CANCELLED") ? LocalDateTime.of(2026, 11, 10, 15, 0) : null, false);
    }
}
