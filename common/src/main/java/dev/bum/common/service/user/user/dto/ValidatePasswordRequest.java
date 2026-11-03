package dev.bum.common.service.user.user.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ValidatePasswordRequest {

    // 사용자 ID.
    String userId;

    // 비밀번호.
    @ToString.Exclude
    String password;
}
