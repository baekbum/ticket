package dev.bum.payment_gateway_service.config;

import dev.bum.payment_gateway_service.security.FinancialSimulationAuthenticationFilter;
import dev.bum.payment_gateway_service.security.FinancialSimulationToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class FinancialSimulationHttpConfig {

    @Bean
    public RestClient financialSimulationRestClient(
            RestClient.Builder builder,
            FinancialSimulationToken token,
            @Value("${app.financial-simulation.connect-timeout:2s}") Duration connectTimeout,
            @Value("${app.financial-simulation.read-timeout:15s}") Duration readTimeout
    ) {

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);

        return builder.requestFactory(factory)
                .defaultHeader(FinancialSimulationAuthenticationFilter.TOKEN_HEADER, token.value())
                .build();
    }
}
