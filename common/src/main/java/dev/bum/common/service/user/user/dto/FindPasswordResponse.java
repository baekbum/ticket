package dev.bum.common.service.user.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FindPasswordResponse {

    // 비밀번호 재설정 토큰.
    private String resetToken;
}
