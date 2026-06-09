package dev.bum.user_service.vo;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class ValidatePasswordInfo {
    String userId;
    String password;
}
