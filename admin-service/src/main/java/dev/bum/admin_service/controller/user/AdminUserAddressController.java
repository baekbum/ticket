package dev.bum.admin_service.controller.user;

import dev.bum.admin_service.feign.user.UserServiceClient;
import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.user.address.dto.UserAddressCondRequest;
import dev.bum.common.service.user.address.dto.UserAddressResponse;
import dev.bum.common.service.user.address.dto.UpdateUserAddressRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class AdminUserAddressController {

    private final UserServiceClient userServiceClient;

    @PostMapping("/address/select/user/{userId}")
    public ResponseEntity<CustomPageResponse<UserAddressResponse>> selectAddressByUserId(
            @PathVariable("userId") String userId,
            @RequestBody UserAddressCondRequest cond
    ) {
        return ResponseEntity.ok(userServiceClient.selectAddressByUserId(userId, cond));
    }

    @PutMapping("/address/update/id/{addressId}")
    public ResponseEntity<UserAddressResponse> updateAddress(
            @PathVariable("addressId") Long addressId,
            @Valid @RequestBody UpdateUserAddressRequest info
    ) {
        return ResponseEntity.ok(userServiceClient.updateAddress(addressId, info));
    }

    @DeleteMapping("/address/delete/id/{addressId}")
    public ResponseEntity<UserAddressResponse> deleteAddress(@PathVariable("addressId") Long addressId) {
        return ResponseEntity.ok(userServiceClient.deleteAddress(addressId));
    }
}
