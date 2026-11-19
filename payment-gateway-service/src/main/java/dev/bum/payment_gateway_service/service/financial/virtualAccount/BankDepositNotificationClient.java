package dev.bum.payment_gateway_service.service.financial.virtualAccount;

import dev.bum.payment_gateway_service.dto.bank.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class BankDepositNotificationClient {

    // 현재 같은 앱의 은행·PG 경계에서도 금융 전용 토큰을 사용한다.
    private final RestClient financialSimulationRestClient;
    // loopback 실행 시 실제 수신 서버 포트를 확인한다.
    private final Environment environment;
    // 요청 본문으로 바꿀 수 없는 서버 설정의 PG 수신 주소.
    @Value("${app.bank.gateway-base-url:}")
    private String configuredBaseUrl;

    /**
     * PG가 입금·가맹점 통보 의도를 커밋한 확인 응답만 수락한다.
     */
    public BankDepositAcknowledgement send(BankDepositEvent event) {

        String base = configuredBaseUrl.isBlank()
                ? "http://127.0.0.1:" + environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"))
                : configuredBaseUrl;
        URI url = URI.create(base.replaceAll("/+$", "") + "/internal/financial-simulations/gateway/bank-deposits");
        ResponseEntity<BankDepositAcknowledgement> received = financialSimulationRestClient.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON).header("X-PG-Event-Id", event.eventId()).body(event)
                .retrieve().toEntity(BankDepositAcknowledgement.class);
        BankDepositAcknowledgement response = received.getBody();

        if (received.getStatusCode().value() != 200 || response == null || !response.matches(event)) {
            throw new IllegalStateException("PG 입금 수신 확인이 최초 이벤트와 일치하지 않습니다.");
        }

        return response;
    }
}
