package dev.bum.payment_gateway_service.service.gateway.client;

import dev.bum.payment_gateway_service.controller.advice.GlobalExceptionHandler;
import dev.bum.payment_gateway_service.dto.card.GatewayCardPaymentApproveRequest;
import dev.bum.payment_gateway_service.exception.*;
import dev.bum.payment_gateway_service.service.gateway.card.GatewayCardApiService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FinancialSimulationResponseContractTest {

    MockRestServiceServer server;
    FinancialSimulationGatewayService financial;
    final String path = "http://127.0.0.1:8099/internal/financial-simulations/cards/approve";

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        financial = new FinancialSimulationGatewayService(builder.build(), new MockEnvironment());
    }

    @AfterEach
    void verifyRequests() { server.verify(); }

    @Test
    void successfulJsonIsReadAsTheDeclaredFinancialDtoAndRetainsAmounts() {
        server.expect(requestTo(path)).andExpect(header("Accept", "application/json"))
                .andExpect(header("X-PG-User-Id", "buyer")).andRespond(withSuccess("""
                        {"paymentNo":"PAY-1","transactionId":"CARD-1","userId":"buyer","approved":true,
                         "approvedAmount":123.45,"currentMonthUsedAmount":123.45,"limitAmount":1000}
                        """, MediaType.APPLICATION_JSON));
        var response = financial.approveCard("buyer", request());
        assertThat(response.paymentNo()).isEqualTo("PAY-1");
        assertThat(response.approved()).isTrue();
        assertThat(response.approvedAmount()).isEqualByComparingTo("123.45");
        assertThat(response.limitAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void pendingHasItsOwnDtoAndPreservesThe202FieldsInsteadOfReturningApprovalSuccess() {
        server.expect(requestTo(path)).andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"paymentNo":"PAY-1","status":"PENDING","message":"기존 대기 안내"}
                        """));
        assertThatThrownBy(() -> financial.approveCard("buyer", request()))
                .isInstanceOfSatisfying(FinancialPaymentPendingException.class, exception -> {
                    var response = new GlobalExceptionHandler().handleFinancialPending(exception);
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                    assertThat(response.getBody().paymentNo()).isEqualTo("PAY-1");
                    assertThat(response.getBody().status()).isEqualTo("PENDING");
                    assertThat(response.getBody().message()).isEqualTo("기존 대기 안내");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAY-OTHER", "PAY-1"})
    void aDifferentPaymentOrBuyerResponseCannotBecomePgApprovalSuccess(String paymentNo) {
        String buyer = paymentNo.equals("PAY-1") ? "other-buyer" : "buyer";
        server.expect(requestTo(path)).andRespond(withSuccess("""
                {"paymentNo":"%s","userId":"%s","transactionId":"CARD-1","approved":true}
                """.formatted(paymentNo, buyer), MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new GatewayCardApiService(financial).approve("buyer", request()))
                .isInstanceOf(FinancialProviderResponseException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 503})
    void businessAndStorageErrorsKeepTheirStatusCodeAndKnownFields(int status) {
        server.expect(requestTo(path)).andRespond(withStatus(HttpStatusCode.valueOf(status))
                .contentType(MediaType.APPLICATION_JSON).body("""
                        {"code":"ORIGINAL_CODE","message":"원래 오류 안내"}
                        """));
        assertThatThrownBy(() -> financial.approveCard("buyer", request()))
                .isInstanceOfSatisfying(FinancialApiException.class, exception -> {
                    var response = new GlobalExceptionHandler().handleFinancialApiError(exception);
                    assertThat(response.getStatusCode().value()).isEqualTo(status);
                    assertThat(response.getBody().code()).isEqualTo("ORIGINAL_CODE");
                    assertThat(response.getBody().message()).isEqualTo("원래 오류 안내");
                    assertThat(exception.getMessage()).doesNotContain("원래 오류 안내");
                });
    }

    @Test
    void emptyAuthenticationErrorKeepsItsStatusAndEmptyBody() {
        server.expect(requestTo(path)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> financial.approveCard("buyer", request()))
                .isInstanceOfSatisfying(FinancialApiException.class, exception -> {
                    var response = new GlobalExceptionHandler().handleFinancialApiError(exception);
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(response.getBody()).isNull();
                });
    }

    @Test
    void frameworkErrorsKeepTheirExistingTimestampNumericStatusErrorAndPath() {
        server.expect(requestTo(path)).andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"timestamp":"2026-11-09T12:00:00Z","status":403,"error":"Forbidden","path":"/original-path"}
                        """));
        assertThatThrownBy(() -> financial.approveCard("buyer", request()))
                .isInstanceOfSatisfying(FinancialApiException.class, exception -> {
                    var body = new GlobalExceptionHandler().handleFinancialApiError(exception).getBody();
                    assertThat(body.timestamp()).isEqualTo("2026-11-09T12:00:00Z");
                    assertThat(body.status()).isEqualTo(403);
                    assertThat(body.error()).isEqualTo("Forbidden");
                    assertThat(body.path()).isEqualTo("/original-path");
                    assertThat(body.code()).isNull();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "{}", "{\"unrelated\":true}"})
    void emptyMalformedOrUnrelatedSuccessfulBodiesCannotBecomeFinancialSuccess(String body) {
        server.expect(requestTo(path)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> financial.approveCard("buyer", request()))
                .isInstanceOf(FinancialProviderResponseException.class);
    }

    @Test
    void malformedPendingDoesNotBecomeSuccessOrAValid202() {
        server.expect(requestTo(path)).andRespond(withStatus(HttpStatus.ACCEPTED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"paymentNo\":\"PAY-1\",\"status\":\"PAID\"}"));
        assertThatThrownBy(() -> financial.approveCard("buyer", request()))
                .isInstanceOf(FinancialProviderResponseException.class);
    }

    @Test
    void approvalExistenceRetainsTheRootJsonBooleanForBothValues() {
        String url = "http://127.0.0.1:8099/internal/financial-simulations/cards/payments/PAY-1/approval-exists";
        server.expect(requestTo(url)).andRespond(withSuccess("false", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url)).andRespond(withSuccess("true", MediaType.APPLICATION_JSON));
        assertThat(financial.approvalExists("PAY-1")).isFalse();
        assertThat(financial.approvalExists("PAY-1")).isTrue();
    }

    GatewayCardPaymentApproveRequest request() {
        return GatewayCardPaymentApproveRequest.builder().paymentNo("PAY-1").amount(new BigDecimal("123.45")).build();
    }
}
