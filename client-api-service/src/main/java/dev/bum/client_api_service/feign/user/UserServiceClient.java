package dev.bum.client_api_service.feign.user;

import dev.bum.common.service.user.user.dto.InsertUserRequest;
import dev.bum.common.service.user.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "client-user-service", url = "${services.user-service.url}", path = "/api/v1")
public interface UserServiceClient {

    @GetMapping("/check/duplication/{userId}")
    void isDuplicated(@PathVariable("userId") String userId);

    @PostMapping("/signup")
    UserResponse signUp(@Valid @RequestBody InsertUserRequest request);
}
