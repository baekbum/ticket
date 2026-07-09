package dev.bum.common.service.user.user.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ValidatePasswordRequest {
    String userId;
    String password;
}
