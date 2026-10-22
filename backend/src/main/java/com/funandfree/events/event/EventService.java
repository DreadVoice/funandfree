package com.funandfree.events.event;

import com.funandfree.events.booking.BookingService;
import com.funandfree.events.common.ConflictException;
import com.funandfree.events.common.ForbiddenException;
import com.funandfree.events.common.NotFoundException;
import com.funandfree.events.event.dto.CreateEventRequest;
import com.funandfree.events.event.dto.UpdateEventRequest;
import com.funandfree.events.user.AppUser;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EventService {

    private final EventRepository events;
    private final BookingService bookings;
    private final TransactionTemplate transactions;

    EventService(EventRepository events, BookingService bookings,
                 PlatformTransactionManager transactionManager) {
        this.events = events;
        this.bookings = bookings;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public Event create(AppUser organizer, CreateEventRequest request) {
        String zone = normalizeZone(request.timeZone());
        requireOrderedTimes(request.startsAtUtc(), request.endsAtUtc());

        return transactions.execute(status -> {
            long id = events.insert(organizer.id(), request.title(), request.description(),
                    request.venue(), request.startsAtUtc(), request.endsAtUtc(), zone,
                    request.latitude(), request.longitude(), request.capacity(), request.imageUrl());
            return require(id);
        });
    }

    public Event update(AppUser organizer, long eventId, UpdateEventRequest request) {
        String zone = normalizeZone(request.timeZone());
        requireOrderedTimes(request.startsAtUtc(), request.endsAtUtc());

        return transactions.execute(status -> {
            Event existing = require(eventId);
            if (existing.organizerId() != organizer.id()) {
                throw new ForbiddenException("event " + eventId + " belongs to another organizer");
            }
            if ("CANCELLED".equals(existing.status())) {
                throw new ConflictException("event " + eventId + " is cancelled");
            }
            if (request.capacity() < existing.seatsTaken()) {
                throw new ConflictException("capacity " + request.capacity()
                        + " is below the " + existing.seatsTaken() + " seats already taken");
            }

            int updated = events.update(eventId, request.title(), request.description(), request.venue(),
                    request.startsAtUtc(), request.endsAtUtc(), zone, request.latitude(),
                    request.longitude(), request.capacity(), request.imageUrl());
            if (updated != 1) {
                throw new ConflictException("event " + eventId + " could not be updated");
            }
            if (request.capacity() > existing.capacity()) {
                bookings.promoteWaitlistToCapacity(eventId);
            }
            return require(eventId);
        });
    }

    public List<Event> forOrganizer(AppUser organizer) {
        return events.findByOrganizer(organizer.id());
    }

    private Event require(long eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> new NotFoundException("event " + eventId + " not found"));
    }

    private String normalizeZone(String timeZone) {
        try {
            return ZoneId.of(timeZone).getId();
        } catch (DateTimeException e) {
            throw new ConflictException("unknown time zone: " + timeZone);
        }
    }

    private void requireOrderedTimes(Instant startsAt, Instant endsAt) {
        if (endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new ConflictException("endsAtUtc must be after startsAtUtc");
        }
    }
}
