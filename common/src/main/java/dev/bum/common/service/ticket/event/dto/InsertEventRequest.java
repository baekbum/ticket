package dev.bum.common.service.ticket.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InsertEventRequest {

    @NotBlank
    private String artistName;

    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String venue;

    @NotNull
    private LocalDateTime eventDateTime;

    @NotNull
    private Integer totalSeats;

    @NotNull
    private Integer maxTicketsPerPerson;
}
