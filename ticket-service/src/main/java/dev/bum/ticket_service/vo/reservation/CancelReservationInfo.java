package dev.bum.ticket_service.vo.reservation;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
public class CancelReservationInfo {
    private List<Long> selectedTicketIdList;
}
