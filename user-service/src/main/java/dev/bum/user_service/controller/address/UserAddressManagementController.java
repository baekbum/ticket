package dev.bum.user_service.controller.address;

import dev.bum.common.feign.dto.CustomPageResponse;
import dev.bum.common.service.user.address.dto.DeleteUserAddressBulkRequest;
import dev.bum.common.service.user.address.dto.InsertUserAddressRequest;
import dev.bum.common.service.user.address.dto.UpdateUserAddressRequest;
import dev.bum.common.service.user.address.dto.UserAddressCondRequest;
import dev.bum.common.service.user.address.dto.UserAddressResponse;
import dev.bum.user_service.service.address.UserAddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1/manage/address")
@RestController
@RequiredArgsConstructor
public class UserAddressManagementController {

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
