package dev.bum.user_service.controller;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.user.dto.UserDto;
import dev.bum.user_service.service.UserService;
import dev.bum.common.service.user.vo.InsertUserInfo;
import dev.bum.common.service.user.vo.UpdateUserInfo;
import dev.bum.common.service.user.vo.UserCond;
import dev.bum.common.service.user.vo.ValidatePasswordInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/api/v1")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * ID 중복 체크 기능
     * 관리자 및 유저 권한 사용가 능
     * @param userId
     * @return
     */
    @GetMapping("/check/duplication/{userId}")
    public ResponseEntity<Void> isDuplicated(@PathVariable("userId") String userId) {
        userService.isDuplicated(userId);
        log.info("[ID 중복 체크 완료 userId: {}]", userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 유저 등록 기능
     * 관리자 및 유저 권한 사용 가능
     * @param info
     * @return
     */
    @PostMapping("/insert")
    public ResponseEntity<UserDto> insert(@Valid @RequestBody InsertUserInfo info) {
        return ResponseEntity.ok(userService.insert(info));
    }

    /**
     * ID로 유저 정보를 검색하는 기능
     * 관리자 권한만 사용 가능
     * @param userId
     * @return
     */
    @GetMapping("/select/id/{userId}")
    public ResponseEntity<UserDto> selectById(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(userService.selectById(userId));
    }

    /**
     * 조건에 따라 유저를 검색하는 기능
     * 관리자 권한만 사용 가능
     * @param cond
     * @return
     */
    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<UserDto>> selectByCond(@RequestBody UserCond cond) {
        return ResponseEntity.ok(userService.selectByCond(cond));
    }

    /**
     * 자기 자신의 정보만 조회 가능
     * 관리자 및 유저 권한 사용 가능
     * @param currentUserId
     * @return
     */
    @GetMapping("/select/me")
    public ResponseEntity<UserDto> selectMyInfo(@AuthenticationPrincipal String currentUserId) {
        // Principal에 저장된 '현재 로그인한 유저의 ID'로만 조회하므로 남의 정보를 볼 방법이 없음!
        return ResponseEntity.ok(userService.selectById(currentUserId));
    }

    /**
     * 자기 자신의 정보만 수정 가능
     * 관리자 및 유저 권한 사용 가능
     * @param currentUserId
     * @param info
     * @return
     */
    @PutMapping("/update/me")
    public ResponseEntity<UserDto> updateMyInfo(
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody UpdateUserInfo info) {
        // 내 아이디로만 업데이트를 수행
        return ResponseEntity.ok(userService.update(currentUserId, info));
    }

    /**
     * 비밀번호가 일치하는지 확인
     * @param info
     * @return
     */
    @PostMapping("/validate/info")
    public ResponseEntity<Void> validateInfo(@Valid @RequestBody ValidatePasswordInfo info) {
        userService.validateInfo(info);
        return ResponseEntity.ok().build();
    }

    /**
     * 유저 정보를 업데이트 하는 기능
     * 관리자만 사용 가능
     * @param userId
     * @param info
     * @return
     */
    @PutMapping("/update/id/{userId}")
    public ResponseEntity<UserDto> update(@PathVariable("userId") String userId, @Valid @RequestBody UpdateUserInfo info) {
        return ResponseEntity.ok(userService.update(userId, info));
    }

    /**
     * 패스워드 초기화
     * @param userId
     * @return
     */
    @PutMapping("/init/password/{userId}")
    public ResponseEntity<Void> initPassword(@PathVariable("userId") String userId) {
        userService.initPassword(userId);
        return ResponseEntity.ok().build();
    }

    /**
     * 유저를 삭제하는 기능
     * 관리자만 사용 가능
     * @param userId
     * @return
     */
    @DeleteMapping("/delete/id/{userId}")
    public ResponseEntity<UserDto> delete(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(userService.delete(userId));
    }
}
