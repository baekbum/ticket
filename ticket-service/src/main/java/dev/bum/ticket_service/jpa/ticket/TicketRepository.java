package dev.bum.ticket_service.jpa.ticket;

import dev.bum.ticket_service.jpa.event.Event;
import dev.bum.ticket_service.jpa.reservation.Reservation;
import dev.bum.ticket_service.jpa.seat.Seat;

import java.util.List;

public interface TicketRepository {
    void insert(List<Ticket> tickets);
    void isExist(Event event, Seat seat);
    Ticket select(long id);
    List<Ticket> selectByReservation(Reservation reservation);
    void cancel(long id);
    void cancelByReservation(Reservation reservation);
    boolean isReservable(String userId, Event event);
}
