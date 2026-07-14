package dev.bum.ticket_service.jpa.ticket;

import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;
import dev.bum.ticket_service.jpa.seat.Seat;

import java.util.List;

public interface TicketRepository {
    void insert(List<Ticket> tickets);
    Ticket select(long id);
    List<Ticket> selectByIdList(List<Long> idList);
    List<Ticket> selectByReservation(Reservation reservation);
    void cancel(long id);
    void cancelByReservation(Reservation reservation);
    boolean isWithinPurchaseLimit(String userId, Event event, int selectedSeatCnt);
}
