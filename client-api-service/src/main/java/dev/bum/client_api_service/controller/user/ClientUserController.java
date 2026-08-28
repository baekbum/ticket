package dev.bum.client_api_service.controller.user;

import dev.bum.client_api_service.feign.user.UserServiceClient;
import dev.bum.common.service.user.user.dto.InsertUserRequest;
import dev.bum.common.service.user.user.dto.UserResponse;
import feign.FeignException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class ClientUserController {

    private final UserServiceClient userServiceClient;

    @GetMapping("/check/duplication/{userId}")
    public ResponseEntity<?> isDuplicated(@PathVariable("userId") String userId) {
        try {
            userServiceClient.isDuplicated(userId);
            return ResponseEntity.ok().build();
        } catch (FeignException e) {
            return ResponseEntity
                    .status(HttpStatusCode.valueOf(e.status()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.contentUTF8());
        }
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signUp(@Valid @RequestBody InsertUserRequest request) {
        try {
            return ResponseEntity.ok(userServiceClient.signUp(request));
        } catch (FeignException e) {
            return ResponseEntity
                    .status(HttpStatusCode.valueOf(e.status()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.contentUTF8());
        }
    }
}
