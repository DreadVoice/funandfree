package com.funandfree.events.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.funandfree.events.event.EventRepository;
import com.funandfree.events.support.TestDatabase;
import com.funandfree.events.user.Role;
import com.funandfree.events.user.UserRepository;
import com.funandfree.events.waitlist.WaitlistRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencyTest {

    private static final int CAPACITY = 10;
    private static final int ATTENDEES = 60;

    @DynamicPropertySource
    static void sqliteOnDisk(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TestDatabase.urlFor("concurrency.db"));
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

    @Test
    void capacityIsNeverExceededUnderContention() throws Exception {
        long organizerId = users.upsert("organizer-concurrency", "org@example.com", "Org", Role.ORGANIZER);
        long eventId = events.insert(organizerId, "Contended Event", "stress test",
                "Main Hall", Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS),
                "UTC", CAPACITY);

        List<Long> userIds = new ArrayList<>();
        for (int i = 0; i < ATTENDEES; i++) {
            userIds.add(users.upsert("attendee-" + i, "a" + i + "@example.com", "A" + i, Role.ATTENDEE));
        }

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(ATTENDEES);
        ConcurrentLinkedQueue<BookingOutcome> outcomes = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (long userId : userIds) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        outcomes.add(bookingService.book(eventId, userId));
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        finished.countDown();
                    }
                });
            }
            startGate.countDown();
            assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(failures).isEmpty();

        Map<BookingOutcome, Long> byOutcome = outcomes.stream()
                .collect(Collectors.groupingBy(o -> o, Collectors.counting()));

        assertThat(byOutcome.getOrDefault(BookingOutcome.CONFIRMED, 0L)).isEqualTo(CAPACITY);
        assertThat(byOutcome.getOrDefault(BookingOutcome.WAITLISTED, 0L)).isEqualTo(ATTENDEES - CAPACITY);
        assertThat(events.seatsTaken(eventId)).isEqualTo(CAPACITY);
        assertThat(bookings.countConfirmed(eventId)).isEqualTo(CAPACITY);
        assertThat(waitlist.countWaiting(eventId)).isEqualTo(ATTENDEES - CAPACITY);
    }

    @Test
    void cancellationTransfersTheSeatToTheFirstWaitlistedUser() {
        long organizerId = users.upsert("organizer-transfer", "org2@example.com", "Org", Role.ORGANIZER);
        long eventId = events.insert(organizerId, "Single Seat", "one seat only",
                "Side Room", Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS),
                "UTC", 1);

        long first = users.upsert("transfer-first", "f@example.com", "First", Role.ATTENDEE);
        long second = users.upsert("transfer-second", "s@example.com", "Second", Role.ATTENDEE);
        long third = users.upsert("transfer-third", "t@example.com", "Third", Role.ATTENDEE);

        assertThat(bookingService.book(eventId, first)).isEqualTo(BookingOutcome.CONFIRMED);
        assertThat(bookingService.book(eventId, second)).isEqualTo(BookingOutcome.WAITLISTED);
        assertThat(bookingService.book(eventId, third)).isEqualTo(BookingOutcome.WAITLISTED);

        assertThat(bookingService.cancel(eventId, first)).contains(second);

        assertThat(events.seatsTaken(eventId)).isEqualTo(1);
        assertThat(bookings.countConfirmed(eventId)).isEqualTo(1);
        assertThat(bookings.findConfirmedByEvent(eventId))
                .singleElement()
                .extracting(Booking::userId)
                .isEqualTo(second);
        assertThat(waitlist.countWaiting(eventId)).isEqualTo(1);
    }
}
