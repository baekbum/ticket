package dev.bum.user_service.controller.address;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.user.address.dto.InsertUserAddressRequest;
import dev.bum.common.service.user.address.dto.UpdateUserAddressRequest;
import dev.bum.common.service.user.address.dto.UserAddressCondRequest;
import dev.bum.common.service.user.address.dto.UserAddressResponse;
import dev.bum.user_service.service.address.UserAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1/address")
@RestController
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService userAddressService;

    @PostMapping("/insert/me")
    public ResponseEntity<UserAddressResponse> insertMyAddress(
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody InsertUserAddressRequest info
    ) {
        return ResponseEntity.ok(userAddressService.insert(currentUserId, info));
    }

    @PostMapping("/select/me")
    public ResponseEntity<CustomPageResponse<UserAddressResponse>> selectMyAddress(
            @AuthenticationPrincipal String currentUserId,
            @RequestBody UserAddressCondRequest cond
    ) {
        return ResponseEntity.ok(userAddressService.selectByUserId(currentUserId, cond));
    }

    @PutMapping("/update/me/{addressId}")
    public ResponseEntity<UserAddressResponse> updateMyAddress(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable("addressId") Long addressId,
            @Valid @RequestBody UpdateUserAddressRequest info
    ) {
        return ResponseEntity.ok(userAddressService.updateMyAddress(currentUserId, addressId, info));
    }

    @DeleteMapping("/delete/me/{addressId}")
    public ResponseEntity<UserAddressResponse> deleteMyAddress(
            @AuthenticationPrincipal String currentUserId,
            @PathVariable("addressId") Long addressId
    ) {
        return ResponseEntity.ok(userAddressService.deleteMyAddress(currentUserId, addressId));
    }
}
