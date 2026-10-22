package com.funandfree.events.booking;

import com.funandfree.events.booking.dto.BookingOutcomeResponse;
import com.funandfree.events.booking.dto.BookingResponse;
import com.funandfree.events.booking.dto.CancellationResponse;
import com.funandfree.events.common.ConflictException;
import com.funandfree.events.security.CurrentUser;
import com.funandfree.events.user.AppUser;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingService bookings;

    BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping("/events/{eventId}/bookings")
    public ResponseEntity<BookingOutcomeResponse> book(@PathVariable long eventId,
                                                       @CurrentUser AppUser user) {
        BookingOutcome outcome = bookings.book(eventId, user.id());
        return switch (outcome) {
            case CONFIRMED -> ResponseEntity.status(HttpStatus.CREATED)
                    .body(new BookingOutcomeResponse(outcome));
            case WAITLISTED -> ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new BookingOutcomeResponse(outcome));
            case ALREADY_BOOKED -> throw new ConflictException("already booked on event " + eventId);
            case ALREADY_WAITLISTED -> throw new ConflictException("already waitlisted on event " + eventId);
        };
    }

    @DeleteMapping("/events/{eventId}/bookings/me")
    public CancellationResponse cancel(@PathVariable long eventId, @CurrentUser AppUser user) {
        Optional<Long> promoted = bookings.cancel(eventId, user.id());
        return new CancellationResponse(promoted.isPresent(), promoted.orElse(null));
    }

    @DeleteMapping("/events/{eventId}/waitlist/me")
    public ResponseEntity<Void> leaveWaitlist(@PathVariable long eventId, @CurrentUser AppUser user) {
        return bookings.leaveWaitlist(eventId, user.id())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/bookings/me")
    public List<BookingResponse> mine(@CurrentUser AppUser user) {
        return bookings.forUser(user.id()).stream().map(BookingResponse::from).toList();
    }
}
