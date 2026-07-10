package dev.bum.user_service.controller.address;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.user.address.dto.*;
import dev.bum.user_service.service.address.UserAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequestMapping("/api/v1/address")
@RestController
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService userAddressService;

    @PostMapping("/insert")
    public ResponseEntity<UserAddressResponse> insert(@Valid @RequestBody InsertUserAddressRequest info) {
        return ResponseEntity.ok(userAddressService.insert(null, info));
    }

    @PostMapping("/insert/user/{userId}")
    public ResponseEntity<UserAddressResponse> insertByUserId(
            @PathVariable("userId") String userId,
            @Valid @RequestBody InsertUserAddressRequest info
    ) {
        return ResponseEntity.ok(userAddressService.insert(userId, info));
    }

    @PostMapping("/insert/me")
    public ResponseEntity<UserAddressResponse> insertMyAddress(
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody InsertUserAddressRequest info
    ) {
        return ResponseEntity.ok(userAddressService.insert(currentUserId, info));
    }

    @GetMapping("/select/id/{addressId}")
    public ResponseEntity<UserAddressResponse> selectById(@PathVariable("addressId") Long addressId) {
        return ResponseEntity.ok(userAddressService.selectById(addressId));
    }

    @PostMapping("/select")
    public ResponseEntity<CustomPageResponse<UserAddressResponse>> selectByCond(@RequestBody UserAddressCondRequest cond) {
        return ResponseEntity.ok(userAddressService.selectByCond(cond));
    }

    @PostMapping("/select/user/{userId}")
    public ResponseEntity<CustomPageResponse<UserAddressResponse>> selectByUserId(
            @PathVariable("userId") String userId,
            @RequestBody UserAddressCondRequest cond
    ) {
        return ResponseEntity.ok(userAddressService.selectByUserId(userId, cond));
    }

    @PostMapping("/select/me")
    public ResponseEntity<CustomPageResponse<UserAddressResponse>> selectMyAddress(
            @AuthenticationPrincipal String currentUserId,
            @RequestBody UserAddressCondRequest cond
    ) {
        return ResponseEntity.ok(userAddressService.selectByUserId(currentUserId, cond));
    }

    @PutMapping("/update/id/{addressId}")
    public ResponseEntity<UserAddressResponse> update(
            @PathVariable("addressId") Long addressId,
            @Valid @RequestBody UpdateUserAddressRequest info
    ) {
        return ResponseEntity.ok(userAddressService.update(addressId, info));
    }

    @DeleteMapping("/delete/id/{addressId}")
    public ResponseEntity<UserAddressResponse> delete(@PathVariable("addressId") Long addressId) {
        return ResponseEntity.ok(userAddressService.delete(addressId));
    }

    @DeleteMapping("/delete/bulk")
    public ResponseEntity<Void> deleteBulk(@Valid @RequestBody DeleteUserAddressBulkRequest info) {
        userAddressService.deleteBulk(info);
        return ResponseEntity.ok().build();
    }
}
