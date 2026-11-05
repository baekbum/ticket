package dev.bum.payment_gateway_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.merchants")
public class MerchantCredentialProperties {

    // 환경 설정으로만 주입하는 자격증명 그룹별 현재/이전 토큰. toString으로 노출하지 않는다.
    private Map<String, List<String>> credentials = new LinkedHashMap<>();

    // 가맹점 자격증명 그룹별 현재·이전 토큰 설정을 반환한다.
    public Map<String, List<String>> getCredentials() {

        return credentials;
    }

    // 환경 설정에서 바인딩한 자격증명 그룹을 저장한다.
    public void setCredentials(Map<String, List<String>> credentials) {

        this.credentials = credentials;
    }
}
