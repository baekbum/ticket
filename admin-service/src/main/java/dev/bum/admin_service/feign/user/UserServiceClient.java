package dev.bum.admin_service.feign.user;

import dev.bum.common.service.user.dto.UserResponse;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.user.dto.InsertUserRequest;
import dev.bum.common.service.user.dto.UpdateUserRequest;
import dev.bum.common.service.user.dto.UserCondRequest;
import dev.bum.common.service.user.dto.ValidatePasswordRequest;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "user-service", url = "${services.user-service.url}", path = "/api/v1")
public interface UserServiceClient {

    @GetMapping("/check/duplication/{userId}")
    void isDuplicated(@PathVariable("userId") String userId);

    @PostMapping("/insert")
    UserResponse insert(@RequestBody InsertUserRequest info);

    @GetMapping("/select/id/{userId}")
    UserResponse selectById(@PathVariable("userId") String userId);

    @PostMapping("/select")
    CustomPageResponse<UserResponse> selectByCond(@RequestBody UserCondRequest cond);

    @PostMapping("/validate/info")
    void validateInfo(@Valid @RequestBody ValidatePasswordRequest info);

    @PutMapping("/update/id/{userId}")
    UserResponse update(@PathVariable("userId") String userId, @Valid @RequestBody UpdateUserRequest info);

    @PutMapping("/init/password/{userId}")
    void initPassword(@PathVariable("userId") String userId);

    @DeleteMapping("/delete/id/{userId}")
    UserResponse delete(@PathVariable("userId") String userId);
}

