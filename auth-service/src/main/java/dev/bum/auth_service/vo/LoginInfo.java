package dev.bum.auth_service.vo;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginInfo {
    private String userId;
    private String password;
}
