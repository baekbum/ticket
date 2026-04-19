package dev.bum.user_service.controller;

import dev.bum.user_service.dto.UserDto;
import dev.bum.user_service.service.UserService;
import dev.bum.user_service.vo.InsertUserInfo;
import dev.bum.user_service.vo.UpdateUserInfo;
import dev.bum.user_service.vo.UserCond;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/api/v1")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/insert")
    public ResponseEntity<UserDto> insert(@Valid @RequestBody InsertUserInfo info) {
        return ResponseEntity.ok(userService.insert(info));
    }

    @PostMapping("/selectAll")
    public ResponseEntity<PagedModel<UserDto>> selectAll(@RequestBody UserCond cond) {
        return ResponseEntity.ok(new PagedModel<>(userService.selectAll(cond)));
    }

    @GetMapping("/select/id/{userId}")
    public ResponseEntity<UserDto> selectById(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(userService.selectById(userId));
    }

    @PostMapping("/select")
    public ResponseEntity<PagedModel<UserDto>> selectByCond(@RequestBody UserCond cond) {
        return ResponseEntity.ok(new PagedModel<>(userService.selectByCond(cond)));
    }

    @PutMapping("/update/id/{userId}")
    public ResponseEntity<UserDto> update(@PathVariable("userId") String userId, @Valid @RequestBody UpdateUserInfo info) {
        return ResponseEntity.ok(userService.update(userId, info));
    }

    @DeleteMapping("/delete/id/{userId}")
    public ResponseEntity<UserDto> delete(@PathVariable("userId") String userId) {
        return ResponseEntity.ok(userService.delete(userId));
    }
}
