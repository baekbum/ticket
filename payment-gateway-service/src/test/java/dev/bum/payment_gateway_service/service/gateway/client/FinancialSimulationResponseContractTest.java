package dev.bum.payment_gateway_service.service.gateway.client;

import dev.bum.common.service.ticket.payment.enums.CardCompany;
import dev.bum.payment_gateway_service.dto.card.FinancialCardApproveRequest;
import dev.bum.payment_gateway_service.dto.card.FinancialCardApprovalResponse;
import dev.bum.payment_gateway_service.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class FinancialSimulationResponseContractTest {

    MockRestServiceServer server;
    FinancialSimulationGatewayService financial;
    final String url="http://127.0.0.1:8099/internal/financial-simulations/cards/gateway/approve";

    @BeforeEach void setup() {
        RestClient.Builder builder=RestClient.builder();
        server=MockRestServiceServer.bindTo(builder).build();
        financial=new FinancialSimulationGatewayService(builder.build(),new MockEnvironment());
    }

    @AfterEach void verifyRequests(){server.verify();}

    @Test void currentFinancialDtoKeepsPgIdAmountAndApprovalFact() {
        server.expect(requestTo(url)).andExpect(method(HttpMethod.POST)).andExpect(headerDoesNotExist("X-Service-Token"))
                .andRespond(withSuccess("""
                {"pgPaymentId":"PG-1","buyerReference":"buyer","cardCompany":"SHINHAN","cardIdentity":"hash",
                 "transactionId":"CARD-1","maskedCardNumber":"4111-****-****-1111","amount":123.45,
                 "approvedAt":"2099-01-01T12:00:00","status":"APPROVED"}
                """,MediaType.APPLICATION_JSON));
        FinancialCardApprovalResponse response = financial.approveGatewayCard(request());
        assertThat(response.pgPaymentId()).isEqualTo("PG-1");
        assertThat(response.amount()).isEqualByComparingTo("123.45");
        assertThat(response.transactionId()).isEqualTo("CARD-1");
    }

    @Test void definiteFinancialRejectionIsDifferentFromUnknownResult() {
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(()->financial.approveGatewayCard(request())).isInstanceOf(FinancialApprovalRejectedException.class);
    }

    @Test void conflictingRequestRemainsAConflict() {
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.CONFLICT));
        assertThatThrownBy(()->financial.approveGatewayCard(request())).isInstanceOf(GatewayPaymentConflictException.class);
    }

    @ParameterizedTest @ValueSource(ints={202,401,403,500,503})
    void otherStatusesNeverBecomeApprovalSuccess(int status) {
        server.expect(requestTo(url)).andRespond(withStatus(HttpStatusCode.valueOf(status)));
        assertThatThrownBy(()->financial.approveGatewayCard(request())).isInstanceOf(FinancialProviderResponseException.class);
    }

    @ParameterizedTest @ValueSource(strings={"","not-json"})
    void missingOrMalformedBodyNeverBecomesApprovalSuccess(String body) {
        server.expect(requestTo(url)).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
        assertThatThrownBy(()->financial.approveGatewayCard(request())).isInstanceOf(FinancialProviderResponseException.class);
    }

    FinancialCardApproveRequest request(){
        return new FinancialCardApproveRequest("PG-1","buyer",CardCompany.SHINHAN,"4111111111111111","123","password",
                new BigDecimal("123.45"),LocalDateTime.of(2099,1,1,12,0));
    }
}
