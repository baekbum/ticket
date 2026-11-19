package dev.bum.payment_gateway_service.service.gateway.client;

import dev.bum.payment_gateway_service.dto.card.*;
import dev.bum.payment_gateway_service.dto.bank.*;
import dev.bum.payment_gateway_service.dto.virtualAccount.*;
import dev.bum.payment_gateway_service.exception.FinancialProviderResponseException;
import dev.bum.payment_gateway_service.exception.GatewayPaymentConflictException;
import dev.bum.payment_gateway_service.exception.FinancialApprovalRejectedException;
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

    // 금융 컨트롤러를 HTTP로 호출하고 성공·대기·오류 본문을 각각 명시적인 DTO로 읽는다.
    private final RestClient financialSimulationRestClient;
    // 같은 애플리케이션의 실제 HTTP 포트를 요청 시점에 확인한다.
    private final Environment environment;
    // 운영 설정으로만 지정하는 고정 대상. 비어 있으면 현재 PG의 loopback 주소를 사용한다.
    @Value("${app.financial-simulation.base-url:}")
    private String configuredBaseUrl;

    public BankAccountIssueGateResponse closeGatewayAccountIssue(BankVirtualAccountIssueRequest request) {

        try {
            return financialSimulationRestClient.post().uri(uri("banks", "virtual-accounts", "gateway", "close-issue"))
                    .contentType(MediaType.APPLICATION_JSON).body(request).exchange((sent, response) -> {
                        BankAccountIssueGateResponse result = response.bodyTo(BankAccountIssueGateResponse.class);

                        if (response.getStatusCode().value() != 200 || result == null || !request.equals(result.request())
                                || result.closed() == (result.account() != null)) {
                            throw new FinancialProviderResponseException();
                        }

                        if (result.account() != null && (!request.pgPaymentId().equals(result.account().pgPaymentId())
                                || request.bankCompany() != result.account().bankCompany() || result.account().amount() == null
                                || request.amount().compareTo(result.account().amount()) != 0
                                || !request.expiresAt().equals(result.account().expiresAt()))) {
                            throw new FinancialProviderResponseException();
                        }

                        return result;
                    });
        } catch (RestClientException failure) {
            throw new FinancialProviderResponseException(failure);
        }
    }

    // 단순 404 조회가 아닌, 이후 승인까지 차단된 금융 확정 결과만 전환 근거로 수용한다.
    public FinancialCardApprovalGateResponse closeGatewayCardApproval(FinancialCardApprovalGateRequest request) {

        try {
            return financialSimulationRestClient.post().uri(uri("cards", "gateway", "close-approval"))
                    .contentType(MediaType.APPLICATION_JSON).body(request).exchange((sent, response) -> {
                        if (response.getStatusCode().value() != 200) {
                            throw new FinancialProviderResponseException();
                        }

                        FinancialCardApprovalGateResponse result = response.bodyTo(FinancialCardApprovalGateResponse.class);

                        if (result == null || !request.equals(result.request()) || result.closed() == (result.approval() != null)) {
                            throw new FinancialProviderResponseException();
                        }

                        return result;
                    });
        } catch (RestClientException failure) {
            throw new FinancialProviderResponseException(failure);
        }
    }

    /**
     * PG가 검증한 원거래로 카드사 원승인을 한 번 요청한다.
     */
    public FinancialCardApprovalResponse approveGatewayCard(FinancialCardApproveRequest request) {

        return financialSimulationRestClient.post().uri(uri("cards", "gateway", "approve"))
                .contentType(MediaType.APPLICATION_JSON).body(request).exchange((sent, response) -> cardResponse(response));
    }

    /**
     * 확정 업무 거절에 대한 원승인 보상을 같은 PG 식별자로 멱등 요청한다.
     */
    public FinancialCardApprovalResponse cancelGatewayCard(String pgPaymentId) {

        return financialSimulationRestClient.post().uri(uri("cards", "gateway", pgPaymentId, "cancel"))
                .exchange((sent, response) -> cardResponse(response));
    }

    /**
     * 응답 유실/PG 저장 실패 후 원승인 금융 사실을 조회해 재승인 없이 복구한다.
     */
    public Optional<FinancialCardApprovalResponse> gatewayCardStatus(String pgPaymentId) {

        return financialSimulationRestClient.get().uri(uri("cards", "gateway", pgPaymentId))
                .exchange((sent, response) -> response.getStatusCode().value() == 404
                        ? Optional.empty() : Optional.of(cardResponse(response)));
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
     * 응답 유실이나 PG 확정 실패 후 최초 은행 입금을 다시 조회한다.
     */
    public Optional<BankVirtualAccountDepositResponse> gatewayDepositStatus(String pgPaymentId) {

        return financialSimulationRestClient.get()
                .uri(uri("banks", "virtual-accounts", "gateway", pgPaymentId, "deposit"))
                .exchange((sent, response) -> response.getStatusCode().value() == 404
                        ? Optional.empty() : Optional.of(depositResponse(response)));
    }

    /**
     * 취소 예매의 입금에 대해 실제 반환 없이 은행 계좌 상태만 정리한다.
     */
    public BankVirtualAccountDepositResponse rejectGatewayDeposit(String pgPaymentId) {

        return financialSimulationRestClient.post()
                .uri(uri("banks", "virtual-accounts", "gateway", pgPaymentId, "reject-deposit"))
                .exchange((sent, response) -> depositResponse(response));
    }

    /**
     * 같은 PG 원거래의 입금 전 계좌 취소를 은행의 별도 트랜잭션으로 요청한다.
     */
    public BankVirtualAccountCancelResponse cancelGatewayAccount(BankVirtualAccountCancelRequest request) {

        BankVirtualAccountCancellationResult result = requestGatewayCancellation(request);

        if (!"CANCELLED".equals(result.outcome())) {
            throw new GatewayPaymentConflictException("은행에서 취소 요청을 거절했습니다.");
        }

        return result.account();
    }

    /**
     * Outbox의 동일 취소 요청을 보내고 성공/업무 거절을 구분한 최종 결과를 받는다.
     */
    public BankVirtualAccountCancellationResult requestGatewayCancellation(BankVirtualAccountCancelRequest request) {

        ResponseEntity<BankVirtualAccountCancellationResult> received = financialSimulationRestClient.post()
                .uri(uri("banks", "virtual-accounts", "gateway", "cancel"))
                .header("X-PG-Event-Id", request.pgPaymentId() + ":CANCEL:INITIAL")
                .contentType(MediaType.APPLICATION_JSON).body(request).retrieve()
                .toEntity(BankVirtualAccountCancellationResult.class);
        BankVirtualAccountCancellationResult result = received.getBody();

        if (received.getStatusCode().value() != 200 || result == null || !result.matches(request)) {
            throw new FinancialProviderResponseException();
        }

        return result;
    }

    /**
     * 은행에 이미 커밋된 취소만 조회해 재시도나 프로세스 중단을 복구한다.
     */
    public Optional<BankVirtualAccountCancelResponse> gatewayCancellationStatus(String pgPaymentId) {

        return financialSimulationRestClient.get()
                .uri(uri("banks", "virtual-accounts", "gateway", pgPaymentId, "cancellation"))
                .exchange((sent, response) -> response.getStatusCode().value() == 404
                        ? Optional.empty() : Optional.of(cancellationResponse(response)));
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
     * 확정 입금 거절과 저장소/통신 실패를 구분한다. 원거래 검증은 PG 확정 단계에서 수행한다.
     */
    private BankVirtualAccountDepositResponse depositResponse(
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response
    ) throws java.io.IOException {

        if (response.getStatusCode().value() == 400 || response.getStatusCode().value() == 409) {
            throw new GatewayPaymentConflictException("은행의 입금 요청이 최초 거래 또는 계좌 조건과 일치하지 않습니다.");
        }

        if (response.getStatusCode().value() != 200) {
            throw new FinancialProviderResponseException();
        }

        try {
            BankVirtualAccountDepositResponse bank = response.bodyTo(BankVirtualAccountDepositResponse.class);

            if (bank == null) {
                throw new FinancialProviderResponseException();
            }

            return bank;
        } catch (RestClientException exception) {
            throw new FinancialProviderResponseException(exception);
        }
    }

    /**
     * 확정 취소 거절과 은행 결과 확인 불가를 구분한다. 원거래 대조는 PG 명령에서 수행한다.
     */
    private BankVirtualAccountCancelResponse cancellationResponse(
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response
    ) throws java.io.IOException {

        if (response.getStatusCode().value() == 400 || response.getStatusCode().value() == 409) {
            throw new GatewayPaymentConflictException("은행 원계좌가 취소 조건과 일치하지 않습니다.");
        }

        if (response.getStatusCode().value() != 200) {
            throw new FinancialProviderResponseException();
        }

        try {
            BankVirtualAccountCancelResponse bank = response.bodyTo(BankVirtualAccountCancelResponse.class);

            if (bank == null) {
                throw new FinancialProviderResponseException();
            }

            return bank;
        } catch (RestClientException exception) {
            throw new FinancialProviderResponseException(exception);
        }
    }

    /**
     * 확정 거절과 금융 결과 확인 불가를 구분하고 검증할 원승인 객체만 반환한다.
     */
    private FinancialCardApprovalResponse cardResponse(
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response
    ) throws java.io.IOException {

        if (response.getStatusCode().value() == 400) {
            throw new FinancialApprovalRejectedException();
        }

        if (response.getStatusCode().value() == 409) {
            throw new GatewayPaymentConflictException("동일 PG 거래의 카드 승인 요청이 일치하지 않습니다.");
        }

        if (response.getStatusCode().value() != 200) {
            throw new FinancialProviderResponseException();
        }

        try {
            FinancialCardApprovalResponse card = response.bodyTo(FinancialCardApprovalResponse.class);

            if (card == null) {
                throw new FinancialProviderResponseException();
            }

            return card;
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
