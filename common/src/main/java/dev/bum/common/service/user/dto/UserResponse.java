package dev.bum.common.service.user.dto;

import dev.bum.common.service.user.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String userId;
    private UserRole role;
    private String name;
    private String phoneNumber;
    private String email;
    private LocalDate birthDate;
    private String address;
    private Boolean isBlacklisted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
