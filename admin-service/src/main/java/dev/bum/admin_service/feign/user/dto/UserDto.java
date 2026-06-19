package dev.bum.admin_service.feign.user.dto;

import dev.bum.admin_service.feign.user.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {

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
