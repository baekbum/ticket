package dev.bum.payment_gateway_service.service.gateway.notification;

import dev.bum.common.service.paymentgateway.notification.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Service
public class StandardBankNotificationAdapter {

    // 모든 가맹점에 공통 입금 DTO를 전송하는 제한 시간·리다이렉트 정책이 고정된 클라이언트.
    private final RestClient client;

    /**
     * 등록 URL만 사용하고 외부 HTTP가 무한 대기하거나 리다이렉트되지 않게 한다.
     */
    public StandardBankNotificationAdapter(RestClient.Builder builder) {

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = builder.clone().requestFactory(factory).build();
    }

    /**
     * 동일 이벤트·최초 입금만 전송하고 응답의 거래/가맹점/계좌/금액을 확인한다.
     */
    public BankDepositNotificationResponse.Outcome send(URI url, String token, BankDepositNotification notification) {

        notification.validateContract();
        BankDepositNotificationResponse response = client.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                .header("X-Service-Token", token).header("X-PG-Event-Id", notification.eventId())
                .header("X-PG-Payment-Id", notification.pgPaymentId()).body(notification)
                .retrieve().body(BankDepositNotificationResponse.class);

        if (response == null || !response.matches(notification)) {
            throw new IllegalStateException("가맹점 입금 통보 응답이 최초 이벤트와 일치하지 않습니다.");
        }

        return response.outcome();
    }
}
