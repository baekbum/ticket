package dev.bum.admin_service.controller.user;

import dev.bum.admin_service.feign.user.UserServiceClient;
import dev.bum.admin_service.feign.user.dto.UserDto;
import dev.bum.admin_service.feign.user.vo.InsertUserInfo;
import dev.bum.admin_service.feign.user.vo.UpdateUserInfo;
import dev.bum.admin_service.feign.user.vo.UserCond;
import dev.bum.admin_service.feign.user.vo.ValidatePasswordInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserServiceClient userServiceClient;

    /**
     * 유저 등록 기능
     * @param info
     * @return
     */
    @PostMapping("/insert")
    public ResponseEntity<UserDto> insert(@Valid @RequestBody InsertUserInfo info) {
        return ResponseEntity.ok(userServiceClient.insert(info));
    }

    /**
     * ID로 유저 정보를 검색하는 기능
     * @param userId
     * @return
     */
    @GetMapping("/select/id/{userId}")
    public ResponseEntity<UserDto> selectById(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(userServiceClient.selectById(userId));
    }

    /**
     * 조건에 따라 유저를 검색하는 기능
     * @param cond
     * @return
     */
    @PostMapping("/select")
    public ResponseEntity<PagedModel<UserDto>> selectByCond(@RequestBody UserCond cond) {
        return ResponseEntity.ok(userServiceClient.selectByCond(cond));
    }

    /**
     * 자기 자신의 정보만 조회 가능
     * @param currentUserId
     * @return
     */
    @GetMapping("/select/me")
    public ResponseEntity<UserDto> selectMyInfo(@AuthenticationPrincipal String currentUserId) {
        UserDto userDto = userServiceClient.selectById(currentUserId);
        return ResponseEntity.ok(userDto);
    }

    /**
     * 자기 자신의 정보만 수정 가능
     * @param currentUserId
     * @param info
     * @return
     */
    @PutMapping("/update/me")
    public ResponseEntity<UserDto> updateMyInfo(
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody UpdateUserInfo info) {
        return ResponseEntity.ok(userServiceClient.update(currentUserId, info));
    }

    /**
     * 비밀번호가 일치하는지 확인
     * @param info
     * @return
     */
    @PostMapping("/validate/info")
    public ResponseEntity<Void> validateInfo(@Valid @RequestBody ValidatePasswordInfo info) {
        userServiceClient.validateInfo(info);
        return ResponseEntity.ok().build();
    }

    /**
     * 유저 정보를 업데이트 하는 기능
     * @param userId
     * @param info
     * @return
     */
    @PutMapping("/update/id/{userId}")
    public ResponseEntity<UserDto> update(@PathVariable("userId") String userId, @Valid @RequestBody UpdateUserInfo info) {
        return ResponseEntity.ok(userServiceClient.update(userId, info));
    }

    /**
     * 패스워드 초기화
     * @param userId
     * @return
     */
    @PutMapping("/init/password/{userId}")
    public ResponseEntity<Void> initPassword(@PathVariable("userId") String userId) {
        userServiceClient.initPassword(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 유저를 삭제하는 기능
     * @param userId
     * @return
     */
    @DeleteMapping("/delete/id/{userId}")
    public ResponseEntity<UserDto> delete(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(userServiceClient.delete(userId));
    }
}
