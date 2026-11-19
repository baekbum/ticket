package dev.bum.payment_gateway_service.service.gateway.refund;

import dev.bum.common.service.paymentgateway.refund.*;
import dev.bum.payment_gateway_service.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.client.*;
import java.net.URI;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class GatewayCardRefundClient {

    // 금융 전용 인증을 포함한 HTTP 클라이언트. 내부 메서드로 카드사 처리를 우회하지 않는다.
    private final RestClient financialSimulationRestClient;

    // loopback의 실제 실행 포트.
    private final Environment environment;

    // 서버에 등록한 카드사 주소.
    @Value("${app.financial-simulation.base-url:}")
    private String configuredBaseUrl;

    /**
     * 카드사 환불·철회만 최초 1회와 추가 3회 시도한다. 확정 4xx는 재시도하지 않는다.
     */
    public PaymentRefundResult send(CardRefundCommand request, boolean withdraw) {

        request.validate();

        for (int attempt = 1; ; attempt++) {
            try {
                String base = configuredBaseUrl.isBlank()
                        ? "http://127.0.0.1:" + environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"))
                        : configuredBaseUrl;
                URI uri = URI.create(base.replaceAll("/+$", "") + "/internal/financial-simulations/cards/gateway/refunds/"
                        + (withdraw ? "withdraw" : "execute"));
                ResponseEntity<PaymentRefundResult> response = financialSimulationRestClient.post().uri(uri)
                        .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().toEntity(PaymentRefundResult.class);
                PaymentRefundResult result = response.getBody();

                if (response.getStatusCode().value() != 200 || result == null || !result.matches(request)) {
                    throw new FinancialProviderResponseException();
                }

                return result;
            } catch (RestClientResponseException failure) {
                if (failure.getStatusCode().is4xxClientError()) {
                    throw new GatewayPaymentConflictException("카드사이 환불 요청을 거절했습니다. 원거래·금액·신청 내용을 확인해주세요.");
                }

                if (attempt == 4 || Thread.currentThread().isInterrupted()) {
                    throw new FinancialProviderResponseException(failure);
                }
            } catch (RestClientException | FinancialProviderResponseException failure) {
                if (attempt == 4 || Thread.currentThread().isInterrupted()) {
                    throw new FinancialProviderResponseException(failure);
                }
            }
        }
    }
}
