package dev.bum.common.service.user.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyMyPasswordRequest {
    @NotBlank
    private String password;
}
