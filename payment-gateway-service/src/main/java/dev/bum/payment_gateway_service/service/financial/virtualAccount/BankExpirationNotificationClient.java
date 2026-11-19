package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import dev.bum.payment_gateway_service.dto.bank.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.client.RestClient;
import java.net.URI;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class BankExpirationNotificationClient {

    // 은행→PG 경계의 금융 전용 인증을 포함하는 HTTP 클라이언트.
    private final RestClient financialSimulationRestClient;
    // 같은 앱의 loopback 수신 서버 포트.
    private final Environment environment;
    // 요청 본문으로 바꿀 수 없는 서버의 PG 수신 주소.
    @Value("${app.bank.gateway-base-url:}")
    private String configuredBaseUrl;

    /**
     * 은행이 확정한 최초 만료 사실을 PG로 전송하고 정확한 수신 커밋 응답만 인정한다.
     */
    public BankExpirationAcknowledgement send(BankExpirationEvent event) {

        event.validate();

        String base = configuredBaseUrl.isBlank()
                ? "http://127.0.0.1:" + environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"))
                : configuredBaseUrl;
        URI url = URI.create(base.replaceAll("/+$", "") + "/internal/financial-simulations/gateway/bank-expirations");
        ResponseEntity<BankExpirationAcknowledgement> received = financialSimulationRestClient.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON).header("X-PG-Event-Id", event.eventId()).body(event)
                .retrieve().toEntity(BankExpirationAcknowledgement.class);
        BankExpirationAcknowledgement response = received.getBody();

        if (received.getStatusCode().value() != 200 || response == null || !response.matches(event)) {
            throw new IllegalStateException("PG 만료 수신 확인이 최초 은행 이벤트와 일치하지 않습니다.");
        }

        return response;
    }
}
