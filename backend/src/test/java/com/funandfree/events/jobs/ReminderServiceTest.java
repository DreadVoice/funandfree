package com.funandfree.events.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import com.funandfree.events.booking.BookingService;
import com.funandfree.events.event.EventRepository;
import com.funandfree.events.support.TestDatabase;
import com.funandfree.events.user.Role;
import com.funandfree.events.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class ReminderServiceTest {

    @DynamicPropertySource
    static void sqliteOnDisk(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TestDatabase.urlFor("reminders.db"));
        registry.add("app.reminders.lead-time", () -> "PT24H");
    }

    @Autowired
    ReminderService reminders;

    @Autowired
    ReminderRepository reminderRepository;

    @Autowired
    EventRepository events;

    @Autowired
    UserRepository users;

    @Autowired
    BookingService bookings;

    private long eventStartingIn(String title, long hours, int capacity) {
        long organizerId = users.upsert("organizer-" + title, "o@example.com", "Org", Role.ORGANIZER);
        return events.insert(organizerId, title, "desc", "Venue",
                Instant.now().plus(hours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS),
                "UTC", capacity);
    }

    private long attendee(String uid) {
        return users.upsert(uid, uid + "@example.com", uid, Role.ATTENDEE);
    }

    @Test
    void sendsOneReminderPerConfirmedBooking() {
        long eventId = eventStartingIn("Due Soon", 5, 10);
        bookings.book(eventId, attendee("due-a"));
        bookings.book(eventId, attendee("due-b"));

        ReminderRunResult result = reminders.run();

        assertThat(result.sent()).isGreaterThanOrEqualTo(2);
        assertThat(result.failed()).isZero();
        assertThat(reminderRepository.countSent(eventId)).isEqualTo(2);
    }

    @Test
    void secondRunSendsNothingForTheSameEvent() {
        long eventId = eventStartingIn("Idempotent", 6, 10);
        bookings.book(eventId, attendee("idem-a"));

        reminders.run();
        int afterFirst = reminderRepository.countSent(eventId);

        ReminderRunResult second = reminders.run();

        assertThat(afterFirst).isEqualTo(1);
        assertThat(reminderRepository.countSent(eventId)).isEqualTo(1);
        assertThat(second.due()).isZero();
    }

    @Test
    void eventsBeyondTheLeadTimeAreNotReminded() {
        long eventId = eventStartingIn("Far Future", 72, 10);
        bookings.book(eventId, attendee("far-a"));

        reminders.run();

        assertThat(reminderRepository.countSent(eventId)).isZero();
    }

    @Test
    void cancelledBookingsAreNotReminded() {
        long eventId = eventStartingIn("Cancelled Booking", 4, 10);
        long userId = attendee("cancel-a");
        bookings.book(eventId, userId);
        bookings.cancel(eventId, userId);

        reminders.run();

        assertThat(reminderRepository.countSent(eventId)).isZero();
    }

    @Test
    void waitlistedAttendeesAreNotReminded() {
        long eventId = eventStartingIn("Single Seat Reminder", 3, 1);
        bookings.book(eventId, attendee("seat-holder"));
        bookings.book(eventId, attendee("seat-waiter"));

        reminders.run();

        assertThat(reminderRepository.countSent(eventId)).isEqualTo(1);
    }

    @Test
    void releasedClaimIsRetriedOnTheNextRun() {
        long eventId = eventStartingIn("Retry", 7, 10);
        long userId = attendee("retry-a");
        bookings.book(eventId, userId);

        reminders.run();
        assertThat(reminderRepository.countSent(eventId)).isEqualTo(1);

        reminderRepository.release(eventId, userId);
        assertThat(reminderRepository.countSent(eventId)).isZero();

        reminders.run();
        assertThat(reminderRepository.countSent(eventId)).isEqualTo(1);
    }
}
