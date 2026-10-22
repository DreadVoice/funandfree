package com.funandfree.events.booking;

import java.time.Instant;

public record Booking(
        long id,
        long eventId,
        long userId,
        BookingStatus status,
        Instant createdAt,
        Instant cancelledAt) {
}
