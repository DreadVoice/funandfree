package com.funandfree.events.spike;

import com.funandfree.events.event.Event;
import com.funandfree.events.event.EventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Profile("spike")
@Component
public class SqliteSpikeRunner implements ApplicationRunner {

    private final JdbcClient db;
    private final EventRepository events;
    private final ConfigurableApplicationContext context;

    SqliteSpikeRunner(JdbcClient db, EventRepository events, ConfigurableApplicationContext context) {
        this.db = db;
        this.events = events;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        final int status = verifyQuietly();
        System.exit(SpringApplication.exit(context, () -> status));
    }

    private int verifyQuietly() {
        try {
            verify();
            return 0;
        } catch (RuntimeException e) {
            System.out.println("--- sqlite spike FAILED: " + e + " ---");
            return 1;
        }
    }

    private void verify() {
        System.out.println("--- sqlite spike start ---");
        System.out.println("sqlite_version=" + db.sql("select sqlite_version()").query(String.class).single());
        System.out.println("journal_mode=" + db.sql("pragma journal_mode").query(String.class).single());
        System.out.println("migrations=" + db.sql("select count(*) from flyway_schema_history where success = 1")
                .query(Integer.class).single());

        long organizerId = db.sql("""
                insert into app_user (firebase_uid, email, display_name, role)
                values ('spike-organizer', 'organizer@example.com', 'Spike Organizer', 'ORGANIZER')
                on conflict (firebase_uid) do update set role = 'ORGANIZER'
                returning id
                """).query(Long.class).single();

        long eventId = events.insert(organizerId, "Jazz Night at the Pier",
                "An evening of live music and improvisation by the water.",
                "Pier 7", Instant.now().plus(14, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS),
                "America/Chicago", 2);
        System.out.println("inserted event id=" + eventId);

        List<Event> upcoming = events.findUpcoming(10);
        System.out.println("findUpcoming -> " + upcoming.size() + " row(s)");
        upcoming.forEach(e -> System.out.println("   " + e.title() + " @ " + e.startsAtUtc()
                + " [" + e.seatsRemaining() + "/" + e.capacity() + " free]"));

        List<Event> hits = events.search("music", 10);
        System.out.println("fts5 search('music') -> " + hits.size() + " row(s)");
        hits.forEach(e -> System.out.println("   " + e.title()));

        boolean first = events.tryClaimSeat(eventId);
        boolean second = events.tryClaimSeat(eventId);
        boolean third = events.tryClaimSeat(eventId);
        int taken = db.sql("select seats_taken from event where id = :id")
                .param("id", eventId).query(Integer.class).single();
        System.out.println("claim #1=" + first + " #2=" + second + " #3=" + third + " (expect true true false)");
        System.out.println("seats_taken=" + taken + " (expect 2)");

        if (!first || !second || third || taken != 2 || upcoming.isEmpty() || hits.isEmpty()) {
            throw new IllegalStateException("spike assertions failed");
        }
        System.out.println("--- sqlite spike ok ---");
    }
}
