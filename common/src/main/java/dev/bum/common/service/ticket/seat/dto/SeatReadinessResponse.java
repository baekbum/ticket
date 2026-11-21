package dev.bum.common.service.ticket.seat.dto;

import dev.bum.common.service.ticket.area.enums.AreaStatus;

import java.util.List;

public record SeatReadinessResponse(
        int totalSeats,
        int unassignedSeats,
        List<Area> areas,
        Cache cache
) {
    public record Area(Long areaId, String areaName, String layoutKey, AreaStatus status, long seatCount) {}

    public record Cache(int expectedKeys, Integer readyKeys) {}
}
