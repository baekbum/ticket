package dev.bum.user_service.controller.user;

import dev.bum.common.service.user.user.dto.InsertUserRequest;
import dev.bum.common.service.user.user.dto.UpdateUserRequest;
import dev.bum.common.service.user.user.dto.UserResponse;
import dev.bum.common.service.user.user.dto.ValidatePasswordRequest;
import dev.bum.user_service.service.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequestMapping("/api/v1")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/check/duplication/{userId}")
    public ResponseEntity<Void> isDuplicated(@PathVariable("userId") String userId) {
        userService.isDuplicated(userId);
        log.info("[ID 중복 체크 완료 userId: {}]", userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signUp(@Valid @RequestBody InsertUserRequest info) {
        return ResponseEntity.ok(userService.insert(info));
    }

    @GetMapping("/select/me")
    public ResponseEntity<UserResponse> selectMyInfo(@AuthenticationPrincipal String currentUserId) {
        return ResponseEntity.ok(userService.selectById(currentUserId));
    }

    @PutMapping("/update/me")
    public ResponseEntity<UserResponse> updateMyInfo(
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody UpdateUserRequest info
    ) {
        return ResponseEntity.ok(userService.update(currentUserId, info));
    }

    @PostMapping("/validate/info")
    public ResponseEntity<Void> validateInfo(@Valid @RequestBody ValidatePasswordRequest info) {
        userService.validateInfo(info);
        return ResponseEntity.ok().build();
    }
}
