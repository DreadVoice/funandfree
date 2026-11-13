package com.funandfree.events.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.funandfree.events.support.TestDatabase;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class OutboxRelayTest {

    @DynamicPropertySource
    static void sqliteOnDisk(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TestDatabase.urlFor("outbox.db"));
        registry.add("app.outbox.max-attempts", () -> "3");
        registry.add("app.outbox.base-backoff", () -> "PT0S");
        registry.add("app.outbox.max-backoff", () -> "PT0S");
        registry.add("app.outbox.visibility-timeout", () -> "PT0S");
    }

    @TestConfiguration
    static class StubHandlers {

        @Bean
        FlakyHandler flakyHandler() {
            return new FlakyHandler();
        }
    }

    static class FlakyHandler implements OutboxHandler {

        static final String TOPIC = "test.flaky";

        int calls;
        int failuresBeforeSuccess;
        boolean permanent;

        @Override
        public String topic() {
            return TOPIC;
        }

        @Override
        public void handle(String payload) {
            calls++;
            if (permanent) {
                throw new OutboxPermanentFailure("will never work");
            }
            if (calls <= failuresBeforeSuccess) {
                throw new IllegalStateException("transient boom " + calls);
            }
        }

        void reset() {
            calls = 0;
            failuresBeforeSuccess = 0;
            permanent = false;
        }
    }

    @Autowired
    OutboxRelay relay;

    @Autowired
    OutboxRepository outbox;

    @Autowired
    FlakyHandler flaky;

    @BeforeEach
    void drainAndReset() {
        flaky.reset();
        while (relay.run().claimed() > 0) {
            flaky.reset();
        }
    }

    @Test
    void deliversAndMarksSent() {
        String key = "deliver-" + System.nanoTime();
        assertThat(outbox.enqueue(FlakyHandler.TOPIC, "{}", key)).isTrue();

        OutboxRunResult result = relay.run();

        assertThat(result.sent()).isEqualTo(1);
        assertThat(result.dead()).isZero();
        assertThat(flaky.calls).isEqualTo(1);
        assertThat(outbox.statusOf(key)).contains("SENT");
    }

    @Test
    void transientFailureIsRetriedAndThenSucceeds() {
        String key = "retry-" + System.nanoTime();
        flaky.failuresBeforeSuccess = 1;
        outbox.enqueue(FlakyHandler.TOPIC, "{}", key);

        OutboxRunResult result = relay.run();

        assertThat(result.retried()).isEqualTo(1);
        assertThat(result.sent()).isEqualTo(1);
        assertThat(flaky.calls).isEqualTo(2);
        assertThat(outbox.statusOf(key)).contains("SENT");
        assertThat(outbox.lastErrorFor(key)).isEmpty();
    }

    @Test
    void positiveBackoffDefersTheRetryToALaterRun() {
        String key = "deferred-" + System.nanoTime();
        flaky.failuresBeforeSuccess = 1;
        outbox.enqueue(FlakyHandler.TOPIC, "{}", key);

        OutboxRelay deferring = new OutboxRelay(outbox, java.util.List.of(flaky), 3, 50,
                Duration.ofMinutes(5), Duration.ofHours(1), Duration.ZERO);

        OutboxRunResult first = deferring.run();

        assertThat(first.retried()).isEqualTo(1);
        assertThat(first.sent()).isZero();
        assertThat(flaky.calls).isEqualTo(1);
        assertThat(outbox.statusOf(key)).contains("PENDING");

        OutboxRunResult second = deferring.run();

        assertThat(second.claimed()).isZero();
        assertThat(flaky.calls).isEqualTo(1);
    }

    @Test
    void deadLettersAfterMaxAttempts() {
        String key = "dead-" + System.nanoTime();
        flaky.failuresBeforeSuccess = Integer.MAX_VALUE;
        outbox.enqueue(FlakyHandler.TOPIC, "{}", key);

        int dead = 0;
        for (int i = 0; i < 10 && dead == 0; i++) {
            dead += relay.run().dead();
        }

        assertThat(dead).isEqualTo(1);
        assertThat(outbox.statusOf(key)).contains("DEAD");
        assertThat(flaky.calls).isEqualTo(3);
    }

    @Test
    void permanentFailureSkipsRetriesEntirely() {
        String key = "permanent-" + System.nanoTime();
        flaky.permanent = true;
        outbox.enqueue(FlakyHandler.TOPIC, "{}", key);

        OutboxRunResult result = relay.run();

        assertThat(result.dead()).isEqualTo(1);
        assertThat(result.retried()).isZero();
        assertThat(flaky.calls).isEqualTo(1);
        assertThat(outbox.statusOf(key)).contains("DEAD");
    }

    @Test
    void unknownTopicIsDeadLetteredWithoutAHandler() {
        String key = "orphan-" + System.nanoTime();
        outbox.enqueue("test.nobody-listens", "{}", key);

        OutboxRunResult result = relay.run();

        assertThat(result.dead()).isEqualTo(1);
        assertThat(outbox.statusOf(key)).contains("DEAD");
    }

    @Test
    void dedupKeyRejectsADuplicateEnqueue() {
        String key = "dedup-" + System.nanoTime();

        assertThat(outbox.enqueue(FlakyHandler.TOPIC, "{}", key)).isTrue();
        assertThat(outbox.enqueue(FlakyHandler.TOPIC, "{}", key)).isFalse();
    }

    @Test
    void nullDedupKeysDoNotCollide() {
        assertThat(outbox.enqueue(FlakyHandler.TOPIC, "{}", null)).isTrue();
        assertThat(outbox.enqueue(FlakyHandler.TOPIC, "{}", null)).isTrue();
    }

    @Test
    void abandonedInFlightMessageIsReleasedForRedelivery() {
        String key = "stale-" + System.nanoTime();
        outbox.enqueue(FlakyHandler.TOPIC, "{}", key);

        Instant now = Instant.now();
        Optional<OutboxMessage> claimed = outbox.claimNext(now, now);
        assertThat(claimed).isPresent();
        assertThat(outbox.statusOf(key)).contains("SENDING");

        assertThat(outbox.releaseStale(now.plusSeconds(1))).isEqualTo(1);
        assertThat(outbox.statusOf(key)).contains("PENDING");
    }

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        OutboxRelay bounded = new OutboxRelay(outbox, java.util.List.of(), 5, 50,
                Duration.ofSeconds(30), Duration.ofHours(1), Duration.ofMinutes(5));

        assertThat(bounded.backoffFor(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(bounded.backoffFor(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(bounded.backoffFor(3)).isEqualTo(Duration.ofSeconds(120));
        assertThat(bounded.backoffFor(8)).isEqualTo(Duration.ofHours(1));
        assertThat(bounded.backoffFor(99)).isEqualTo(Duration.ofHours(1));
    }
}
