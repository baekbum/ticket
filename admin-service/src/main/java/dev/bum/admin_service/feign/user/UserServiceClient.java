package dev.bum.admin_service.feign.user;

import dev.bum.admin_service.feign.user.dto.UserDto;
import dev.bum.admin_service.feign.user.vo.InsertUserInfo;
import dev.bum.admin_service.feign.user.vo.UpdateUserInfo;
import dev.bum.admin_service.feign.user.vo.UserCond;
import dev.bum.admin_service.feign.user.vo.ValidatePasswordInfo;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "user-service", url = "${services.user-service.url}", path = "/api/v1")
public interface UserServiceClient {

    @GetMapping("/check/duplication/{userId}")
    void isDuplicated(@PathVariable("userId") String userId);

    @PostMapping("/insert")
    UserDto insert(@RequestBody InsertUserInfo info);

    @GetMapping("/select/id/{userId}")
    UserDto selectById(@PathVariable("userId") String userId);

    @PostMapping("/select")
    PagedModel<UserDto> selectByCond(@RequestBody UserCond cond);

    @PostMapping("/validate/info")
    void validateInfo(@Valid @RequestBody ValidatePasswordInfo info);

    @PutMapping("/update/id/{userId}")
    UserDto update(@PathVariable("userId") String userId, @Valid @RequestBody UpdateUserInfo info);

    @PutMapping("/init/password/{userId}")
    void initPassword(@PathVariable("userId") String userId);

    @DeleteMapping("/delete/id/{userId}")
    UserDto delete(@PathVariable("userId") String userId);
}

