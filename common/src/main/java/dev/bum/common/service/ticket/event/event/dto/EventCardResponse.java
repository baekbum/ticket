package dev.bum.common.service.ticket.event.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventCardResponse {

    private String eventGroupCode;
    private String artistName;
    private String title;
    private String posterUrl;
    private LocalDate eventStartDate;
    private LocalDate eventEndDate;
}
