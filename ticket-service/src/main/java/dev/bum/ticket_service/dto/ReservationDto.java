package dev.bum.ticket_service.dto;

import dev.bum.ticket_service.enums.ReservationStatus;
import dev.bum.ticket_service.jpa.reservation.Reservation;
import dev.bum.ticket_service.jpa.ticket.Ticket;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationDto {

    private long reservationId; // 예매번호
    private String userId; // 유저 ID
    private long eventId; // 이벤트 ID
    private String eventTitle; // 이벤트 명
    private String reservedDate; // 예매일 2025년 8월 11일
    private String eventDateTime; // 관람일 2025년 9월 14일 16시
    private String venue; // 공연장소
    private int ticketCount; // 매수
    private String status; // 예매 상태

    // 좌석 정보
    private List<TicketDto> tickets;

    public ReservationDto(Reservation reservation) {
        this.reservationId = reservation.getReservationId();
        this.userId = reservation.getUserId();

        this.eventId = reservation.getEvent().getEventId();
        this.eventTitle = reservation.getEvent().getTitle();

        DateTimeFormatter reservedFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일");
        this.reservedDate = reservation.getReservedAt().format(reservedFormatter);

        DateTimeFormatter eventFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH시");
        this.eventDateTime = reservation.getEvent().getEventDateTime().format(eventFormatter);

        this.venue = reservation.getEvent().getVenue();
        this.ticketCount = reservation.getTickets().size();
        this.status = reservation.getStatus().name();

        for (Ticket ticket : reservation.getTickets()) {
            TicketDto dto = TicketDto.builder()
                    .ticketId(ticket.getTicketId())
                    .seatNumber(ticket.getSeat().getSeatNumber())
                    .grade(ticket.getSeat().getGrade().name())
                    .price(ticket.getSeat().getPrice())
                    .status(ticket.getSeat().getStatus().name())
                    .build();

            tickets.add(dto);
        }
    }
}
