package dev.bum.ticket_service.vo.event;

import dev.bum.ticket_service.enums.EventStatus;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class InsertEventInfo {

    @NotBlank
    private String artistName;

    @NotBlank
    private String title;

    private String description;

    @NotBlank
    private String venue;

    @NotNull
    private LocalDateTime eventDate;

    @NotNull
    private Integer totalSeats;

    @NotNull
    private Integer maxTicketsPerPerson;
}
