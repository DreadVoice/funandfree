package com.funandfree.events.event.dto;

import com.funandfree.events.event.Event;
import java.time.Instant;

public record EventResponse(
        long id,
        long organizerId,
        String title,
        String description,
        String venue,
        Instant startsAtUtc,
        String timeZone,
        int capacity,
        int seatsTaken,
        int seatsRemaining,
        String status) {

    public static EventResponse from(Event event) {
        return new EventResponse(event.id(), event.organizerId(), event.title(), event.description(),
                event.venue(), event.startsAtUtc(), event.timeZone(), event.capacity(),
                event.seatsTaken(), event.seatsRemaining(), event.status());
    }
}
