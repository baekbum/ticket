package dev.bum.ticket_service.vo.event;

import dev.bum.ticket_service.enums.EventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class UpdateEventInfo {
    private String artistName;
    private String title;
    private String description;
    private String venue;
    private LocalDateTime eventDate;
    private Integer totalSeats;
    private EventStatus status;
    private Integer maxTicketsPerPerson;
}
