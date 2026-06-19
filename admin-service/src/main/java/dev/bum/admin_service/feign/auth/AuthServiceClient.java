package dev.bum.admin_service.feign.auth;

import dev.bum.common.jwt.dto.TokenDto;
import dev.bum.common.service.auth.vo.LoginInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "auth-service", url = "${services.auth-service.url}", path = "/api/v1")
public interface AuthServiceClient {

    @PostMapping("/login")
    TokenDto login(@RequestBody LoginInfo info);
}
