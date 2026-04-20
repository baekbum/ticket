package dev.bum.common.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDtoForEvent {
    String eventType; // CREATE, DELETE
    private Long id;
    private String userId;
    private String password;
    private String role;
}
