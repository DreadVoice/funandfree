package com.funandfree.events.booking;

import com.funandfree.events.common.ConflictException;
import com.funandfree.events.common.NotFoundException;
import com.funandfree.events.event.Event;
import com.funandfree.events.event.EventRepository;
import com.funandfree.events.waitlist.WaitlistRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingService {

    private final EventRepository events;
    private final BookingRepository bookings;
    private final WaitlistRepository waitlist;
    private final TransactionTemplate transactions;

    BookingService(EventRepository events, BookingRepository bookings, WaitlistRepository waitlist,
                   PlatformTransactionManager transactionManager) {
        this.events = events;
        this.bookings = bookings;
        this.waitlist = waitlist;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public BookingOutcome book(long eventId, long userId) {
        return transactions.execute(status -> {
            Event event = events.findById(eventId)
                    .orElseThrow(() -> new NotFoundException("event " + eventId + " not found"));
            if (!"PUBLISHED".equals(event.status())) {
                throw new ConflictException("event " + eventId + " is not open for booking");
            }
            if (bookings.hasActiveBooking(eventId, userId)) {
                return BookingOutcome.ALREADY_BOOKED;
            }
            if (events.tryClaimSeat(eventId)) {
                bookings.insertConfirmed(eventId, userId);
                return BookingOutcome.CONFIRMED;
            }
            if (waitlist.isWaiting(eventId, userId)) {
                return BookingOutcome.ALREADY_WAITLISTED;
            }
            waitlist.insertWaiting(eventId, userId);
            return BookingOutcome.WAITLISTED;
        });
    }

    public Optional<Long> cancel(long eventId, long userId) {
        return transactions.execute(status -> {
            if (bookings.cancelActive(eventId, userId) == 0) {
                throw new NotFoundException("no active booking for user " + userId + " on event " + eventId);
            }
            Optional<Long> promoted = waitlist.promoteNext(eventId);
            if (promoted.isPresent()) {
                bookings.insertConfirmed(eventId, promoted.get());
            } else {
                events.releaseSeat(eventId);
            }
            return promoted;
        });
    }

    public boolean leaveWaitlist(long eventId, long userId) {
        return transactions.execute(status -> waitlist.cancelWaiting(eventId, userId) > 0);
    }

    public List<Booking> forUser(long userId) {
        return bookings.findByUser(userId);
    }
}
