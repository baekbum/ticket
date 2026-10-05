package dev.bum.common.service.user.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class VerifyMyPasswordRequest {
    @NotBlank
    @ToString.Exclude
    private String password;
}
