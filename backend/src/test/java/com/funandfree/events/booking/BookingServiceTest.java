package com.funandfree.events.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funandfree.events.common.NotFoundException;
import com.funandfree.events.event.EventRepository;
import com.funandfree.events.support.TestDatabase;
import com.funandfree.events.user.Role;
import com.funandfree.events.user.UserRepository;
import com.funandfree.events.waitlist.WaitlistRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class BookingServiceTest {

    @DynamicPropertySource
    static void sqliteOnDisk(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TestDatabase.urlFor("service.db"));
    }

    @Autowired
    BookingService bookingService;

    @Autowired
    BookingRepository bookings;

    @Autowired
    WaitlistRepository waitlist;

    @Autowired
    EventRepository events;

    @Autowired
    UserRepository users;

    private long newEvent(String uid, String title, int capacity) {
        long organizerId = users.upsert(uid, uid + "@example.com", "Org", Role.ORGANIZER);
        return events.insert(organizerId, title, "live music and more", "Venue",
                Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS), "UTC", capacity);
    }

    @Test
    void bookingTwiceIsRejected() {
        long eventId = newEvent("org-double", "Double Booking", 5);
        long userId = users.upsert("double-booker", "d@example.com", "D", Role.ATTENDEE);

        assertThat(bookingService.book(eventId, userId)).isEqualTo(BookingOutcome.CONFIRMED);
        assertThat(bookingService.book(eventId, userId)).isEqualTo(BookingOutcome.ALREADY_BOOKED);
        assertThat(events.seatsTaken(eventId)).isEqualTo(1);
    }

    @Test
    void waitlistingTwiceIsRejected() {
        long eventId = newEvent("org-wl", "Waitlist Twice", 1);
        long holder = users.upsert("wl-holder", "h@example.com", "H", Role.ATTENDEE);
        long waiter = users.upsert("wl-waiter", "w@example.com", "W", Role.ATTENDEE);

        assertThat(bookingService.book(eventId, holder)).isEqualTo(BookingOutcome.CONFIRMED);
        assertThat(bookingService.book(eventId, waiter)).isEqualTo(BookingOutcome.WAITLISTED);
        assertThat(bookingService.book(eventId, waiter)).isEqualTo(BookingOutcome.ALREADY_WAITLISTED);
        assertThat(waitlist.countWaiting(eventId)).isEqualTo(1);
    }

    @Test
    void cancellingWithNoWaitlistReleasesTheSeat() {
        long eventId = newEvent("org-release", "Release Seat", 2);
        long userId = users.upsert("releaser", "r@example.com", "R", Role.ATTENDEE);

        bookingService.book(eventId, userId);
        assertThat(events.seatsTaken(eventId)).isEqualTo(1);

        assertThat(bookingService.cancel(eventId, userId)).isEmpty();
        assertThat(events.seatsTaken(eventId)).isZero();
        assertThat(bookings.countConfirmed(eventId)).isZero();
    }

    @Test
    void cancellingWithoutABookingFails() {
        long eventId = newEvent("org-missing", "No Booking", 2);
        long userId = users.upsert("ghost", "g@example.com", "G", Role.ATTENDEE);

        assertThatThrownBy(() -> bookingService.cancel(eventId, userId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void bookingAnUnknownEventFails() {
        long userId = users.upsert("lost", "l@example.com", "L", Role.ATTENDEE);

        assertThatThrownBy(() -> bookingService.book(999999L, userId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void promotionFollowsJoinOrder() {
        long eventId = newEvent("org-fifo", "Fifo Promotion", 1);
        long holder = users.upsert("fifo-holder", "fh@example.com", "FH", Role.ATTENDEE);
        long early = users.upsert("fifo-early", "fe@example.com", "FE", Role.ATTENDEE);
        long late = users.upsert("fifo-late", "fl@example.com", "FL", Role.ATTENDEE);

        bookingService.book(eventId, holder);
        bookingService.book(eventId, early);
        bookingService.book(eventId, late);

        assertThat(bookingService.cancel(eventId, holder)).contains(early);
        assertThat(bookingService.cancel(eventId, early)).contains(late);
        assertThat(bookingService.cancel(eventId, late)).isEmpty();
        assertThat(events.seatsTaken(eventId)).isZero();
    }

    @Test
    void leavingTheWaitlistFreesTheUserFromPromotion() {
        long eventId = newEvent("org-leave", "Leave Waitlist", 1);
        long holder = users.upsert("leave-holder", "lh@example.com", "LH", Role.ATTENDEE);
        long quitter = users.upsert("leave-quitter", "lq@example.com", "LQ", Role.ATTENDEE);
        long stayer = users.upsert("leave-stayer", "ls@example.com", "LS", Role.ATTENDEE);

        bookingService.book(eventId, holder);
        bookingService.book(eventId, quitter);
        bookingService.book(eventId, stayer);

        assertThat(bookingService.leaveWaitlist(eventId, quitter)).isTrue();
        Optional<Long> promoted = bookingService.cancel(eventId, holder);
        assertThat(promoted).contains(stayer);
    }
}
