package dev.bum.common.service.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MyLoginLogResponse {

    private Long id;
    private String result;
    private String authMethod;
    private String failureReason;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime occurredAt;
}
