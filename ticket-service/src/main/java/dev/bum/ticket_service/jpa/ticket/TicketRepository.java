package dev.bum.ticket_service.jpa.ticket;

import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.reservation.reservation.Reservation;

import java.util.List;

public interface TicketRepository {
    void insert(List<Ticket> tickets);
    Ticket select(long id);
    List<Ticket> selectByIdList(List<Long> idList);
    List<Ticket> selectByReservation(Reservation reservation);
    boolean isWithinPurchaseLimit(String userId, Event event, int selectedSeatCnt);
}
