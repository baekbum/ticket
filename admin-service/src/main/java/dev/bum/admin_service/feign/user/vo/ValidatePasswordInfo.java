package dev.bum.admin_service.feign.user.vo;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class ValidatePasswordInfo {
    String userId;
    String password;
}
