package dev.bum.payment_gateway_service.service.gateway.client;

import dev.bum.common.service.ticket.payment.dto.GatewayVirtualAccountCancelRequest;
import dev.bum.payment_gateway_service.dto.card.*;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.dto.virtualAccount.*;
import dev.bum.payment_gateway_service.exception.FinancialProviderResponseException;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import dev.bum.payment_gateway_service.security.FinancialSimulationAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class FinancialSimulationGatewayService {

    // 기존 응답 상태·본문을 보존하면서 모의 금융 컨트롤러에 실제 HTTP 요청을 보낸다.
    private final RestClient financialSimulationRestClient;
    // 같은 애플리케이션의 실제 HTTP 포트를 요청 시점에 확인한다.
    private final Environment environment;
    // 운영 설정으로만 지정하는 고정 대상. 비어 있으면 현재 PG의 loopback 주소를 사용한다.
    @Value("${app.financial-simulation.base-url:}")
    private String configuredBaseUrl;

    /**
     * 인증된 사용자 ID를 내부 헤더로 전달해 모의 카드 승인 컨트롤러를 호출한다.
     */
    public ResponseEntity<byte[]> approveCard(String userId, GatewayCardPaymentApproveRequest request) {

        return exchange(financialSimulationRestClient.post().uri(uri("cards", "approve"))
                .header(FinancialSimulationAuthenticationFilter.USER_HEADER, userId)
                .contentType(MediaType.APPLICATION_JSON).body(request));
    }

    /**
     * 모의 은행 컨트롤러에 가상계좌 발급을 요청한다.
     */
    public ResponseEntity<byte[]> issueAccount(GatewayVirtualAccountIssueRequest request) {

        return post(request, "banks", "virtual-accounts", "issue");
    }

    /**
     * 저장된 PG 원거래의 일반적인 은행 입력만 전송하고 구조화한 발급 결과를 받는다.
     */
    public BankVirtualAccountIssueResponse issueGatewayAccount(BankVirtualAccountIssueRequest request) {

        return financialSimulationRestClient.post().uri(uri("banks", "virtual-accounts", "gateway", "issue"))
                .contentType(MediaType.APPLICATION_JSON).body(request).exchange((sent, response) -> {
                    if (response.getStatusCode().value() == 400 || response.getStatusCode().value() == 409) {
                        throw new GatewayPaymentConflictException("은행에서 같은 PG 거래의 발급 요청을 거절했습니다.");
                    }

                    return bankResponse(response);
                });
    }

    /**
     * 인증된 사용자가 소유한 카드 거래 상태를 HTTP로 조회한다.
     */
    public ResponseEntity<byte[]> cardStatus(String userId, String paymentNo) {

        return exchange(financialSimulationRestClient.get().uri(uri("cards", "payments", paymentNo))
                .header(FinancialSimulationAuthenticationFilter.USER_HEADER, userId));
    }

    /**
     * Ticksy 복구 처리에 필요한 승인 이력 존재 여부를 조회한다.
     */
    public ResponseEntity<byte[]> approvalExists(String paymentNo) {

        return exchange(financialSimulationRestClient.get().uri(uri("cards", "payments", paymentNo, "approval-exists")));
    }

    /**
     * 모의 은행의 가상계좌 처리 상태를 조회한다.
     */
    public ResponseEntity<byte[]> accountStatus(String paymentNo) {

        return exchange(financialSimulationRestClient.get().uri(uri("banks", "virtual-accounts", paymentNo)));
    }

    /**
     * 금융 응답 유실이나 PG 결과 확정 실패 뒤 같은 PG 식별자로 은행의 기존 결과를 확인한다.
     */
    public Optional<BankVirtualAccountIssueResponse> gatewayAccountStatus(String pgPaymentId) {

        return financialSimulationRestClient.get().uri(uri("banks", "virtual-accounts", "gateway", pgPaymentId))
                .exchange((sent, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        return Optional.empty();
                    }

                    return Optional.of(bankResponse(response));
                });
    }

    /**
     * 최초 환불 프로세스 ID를 그대로 전달해 카드 환불을 요청한다.
     */
    public ResponseEntity<byte[]> refundCard(GatewayCardPaymentRefundRequest request) {

        return post(request, "cards", "refund");
    }

    /**
     * 입금 제공자가 인증한 요청을 모의 은행의 입금 진입점에 전달한다.
     */
    public ResponseEntity<byte[]> deposit(GatewayVirtualAccountDepositRequest request) {

        return post(request, "banks", "virtual-accounts", "deposit");
    }

    /**
     * 최초 환불 프로세스 ID를 유지해 가상계좌 환불을 요청한다.
     */
    public ResponseEntity<byte[]> refundAccount(GatewayVirtualAccountRefundRequest request) {

        return post(request, "banks", "virtual-accounts", "refund");
    }

    /**
     * Ticksy의 입금 전 취소 요청을 모의 은행에 전달한다.
     */
    public ResponseEntity<byte[]> cancelAccount(GatewayVirtualAccountCancelRequest request) {

        return post(request, "banks", "virtual-accounts", "cancel");
    }

    /**
     * 원래 결제번호·작업 식별자를 유지하며 POST를 한 번 수행한다. 업무 재시도 키를 만들지 않는다.
     */
    private ResponseEntity<byte[]> post(Object request, String... path) {

        return exchange(financialSimulationRestClient.post().uri(uri(path))
                .contentType(MediaType.APPLICATION_JSON).body(request));
    }

    /**
     * 202 PENDING 및 금융 거절 응답도 상태·JSON 본문 그대로 돌려준다. 외부 인증 헤더는 전달하지 않는다.
     */
    private ResponseEntity<byte[]> exchange(RestClient.RequestHeadersSpec<?> request) {

        return request.exchange((sent, response) -> {
            MediaType contentType = response.getHeaders().getContentType();

            return ResponseEntity.status(response.getStatusCode())
                    .contentType(contentType == null ? MediaType.APPLICATION_JSON : contentType)
                    .body(response.getBody().readAllBytes());
        });
    }

    /**
     * 잘못된 HTTP 응답을 발급 성공으로 처리하지 않고 금융 결과 확인 불가로 반환한다.
     */
    private BankVirtualAccountIssueResponse bankResponse(
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response
    ) throws java.io.IOException {

        if (response.getStatusCode().value() != 200) {
            throw new FinancialProviderResponseException();
        }

        try {
            BankVirtualAccountIssueResponse bank = response.bodyTo(BankVirtualAccountIssueResponse.class);

            if (bank == null) {
                throw new FinancialProviderResponseException();
            }

            return bank;
        } catch (RestClientException exception) {
            throw new FinancialProviderResponseException(exception);
        }
    }

    /**
     * 요청 본문의 URL을 받지 않고 고정 설정과 안전하게 인코딩한 경로로만 대상을 결정한다.
     */
    private URI uri(String... path) {

        String baseUrl = configuredBaseUrl;

        if (baseUrl == null || baseUrl.isBlank()) {
            int port = environment.getProperty("local.server.port", Integer.class,
                    environment.getProperty("server.port", Integer.class, 8099));
            baseUrl = "http://127.0.0.1:" + port;
        }

        return UriComponentsBuilder.fromUriString(baseUrl).pathSegment("internal", "financial-simulations")
                .pathSegment(path).build().encode().toUri();
    }
}
