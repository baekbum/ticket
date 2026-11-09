package dev.bum.payment_gateway_service.service.gateway.notification;

import dev.bum.common.service.paymentgateway.notification.CardPaymentNotification;
import dev.bum.common.service.paymentgateway.notification.CardPaymentNotificationResponse;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Service
public class StandardCardNotificationAdapter implements MerchantCardNotificationAdapter {

    // 가맹점에 관계없이 같은 요청·응답 규격을 사용하는 단일 HTTP 전송기.
    private final RestClient client;

    /**
     * 연결/읽기 제한을 설정하고 등록된 주소의 리다이렉트를 따라가지 않는다.
     */
    public StandardCardNotificationAdapter(RestClient.Builder builder) {

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = builder.clone().requestFactory(factory).build();
    }

    /**
     * URL을 인자로 받아 경로를 덧붙이지 않고, 모든 가맹점에 같은 DTO와 헤더를 전송한다.
     */
    @Override
    public boolean send(URI url, String serviceToken, CardPaymentNotification notification) {

        notification.validateContract();

        CardPaymentNotificationResponse response = client.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                .header("X-Service-Token", serviceToken)
                .header("X-PG-Event-Id", notification.eventId())
                .header("X-PG-Payment-Id", notification.pgPaymentId())
                .body(notification).retrieve().body(CardPaymentNotificationResponse.class);

        if (response == null || !response.matches(notification)) {
            throw new IllegalStateException("가맹점 통보 응답이 원래 이벤트와 일치하지 않습니다.");
        }

        return response.outcome() == CardPaymentNotificationResponse.Outcome.ACCEPTED;
    }
}
