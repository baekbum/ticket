package dev.bum.client_api_service.feign.user;

import dev.bum.common.service.user.user.dto.FindPasswordRequest;
import dev.bum.common.service.user.user.dto.FindPasswordResponse;
import dev.bum.common.service.user.user.dto.InsertUserRequest;
import dev.bum.common.service.user.user.dto.FindUserIdRequest;
import dev.bum.common.service.user.user.dto.FindUserIdResponse;
import dev.bum.common.service.user.user.dto.ResetPasswordRequest;
import dev.bum.common.service.user.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "client-user-service", url = "${services.user-service.url}", path = "/api/v1")
public interface UserServiceClient {

    @GetMapping("/check/duplication/{userId}")
    void isDuplicated(@PathVariable("userId") String userId);

    @PostMapping("/signup")
    UserResponse signUp(@Valid @RequestBody InsertUserRequest request);

    @PostMapping("/find/id/phone")
    FindUserIdResponse findUserIdByPhoneNumber(@Valid @RequestBody FindUserIdRequest request);

    @PostMapping("/find/id/email")
    FindUserIdResponse findUserIdByEmail(@Valid @RequestBody FindUserIdRequest request);

    @PostMapping("/find/password/phone")
    FindPasswordResponse findPasswordByPhoneNumber(@Valid @RequestBody FindPasswordRequest request);

    @PostMapping("/find/password/email")
    FindPasswordResponse findPasswordByEmail(@Valid @RequestBody FindPasswordRequest request);

    @PostMapping("/reset/password")
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request);

    @GetMapping("/select/me")
    UserResponse selectMyInfo(@RequestHeader("Authorization") String authorizationHeader);
}
