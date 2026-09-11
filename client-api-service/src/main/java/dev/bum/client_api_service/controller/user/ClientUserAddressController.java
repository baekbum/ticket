package dev.bum.client_api_service.controller.user;

import dev.bum.client_api_service.feign.user.UserServiceClient;
import dev.bum.common.service.user.address.dto.UserAddressCondRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/address")
@RequiredArgsConstructor
public class ClientUserAddressController {

    private final UserServiceClient userServiceClient;

    @PostMapping("/select/me")
    public ResponseEntity<?> selectMyAddress(
            @RequestHeader("Authorization") String authorizationHeader,
            @RequestBody UserAddressCondRequest request
    ) {
        try {
            return ResponseEntity.ok(userServiceClient.selectMyAddress(authorizationHeader, request));
        } catch (FeignException e) {
            return ResponseEntity
                    .status(HttpStatusCode.valueOf(e.status()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.contentUTF8());
        }
    }
}
