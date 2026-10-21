package com.funandfree.events.event;

import java.time.Instant;

public record Event(
        long id,
        long organizerId,
        String title,
        String description,
        String venue,
        Instant startsAtUtc,
        String timeZone,
        int capacity,
        int seatsTaken,
        String status) {
    public int seatsRemaining() {
        return capacity - seatsTaken;
    }
}
