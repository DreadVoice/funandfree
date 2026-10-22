package com.funandfree.events.booking.dto;

import com.funandfree.events.booking.Booking;
import com.funandfree.events.booking.BookingStatus;
import java.time.Instant;

public record BookingResponse(
        long id,
        long eventId,
        BookingStatus status,
        Instant createdAt,
        Instant cancelledAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(booking.id(), booking.eventId(), booking.status(),
                booking.createdAt(), booking.cancelledAt());
    }
}
