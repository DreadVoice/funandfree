package com.funandfree.events.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final Map<String, OutboxHandler> handlers;
    private final int maxAttempts;
    private final int batchSize;
    private final Duration baseBackoff;
    private final Duration maxBackoff;
    private final Duration visibilityTimeout;

    OutboxRelay(OutboxRepository outbox,
                List<OutboxHandler> handlers,
                @Value("${app.outbox.max-attempts}") int maxAttempts,
                @Value("${app.outbox.batch-size}") int batchSize,
                @Value("${app.outbox.base-backoff}") Duration baseBackoff,
                @Value("${app.outbox.max-backoff}") Duration maxBackoff,
                @Value("${app.outbox.visibility-timeout}") Duration visibilityTimeout) {
        this.outbox = outbox;
        this.handlers = handlers.stream()
                .collect(Collectors.toMap(OutboxHandler::topic, Function.identity()));
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
        this.baseBackoff = baseBackoff;
        this.maxBackoff = maxBackoff;
        this.visibilityTimeout = visibilityTimeout;
    }

    public OutboxRunResult run() {
        Instant now = Instant.now();
        int released = outbox.releaseStale(now);
        if (released > 0) {
            log.warn("outbox released {} stale messages", released);
        }

        int claimed = 0;
        int sent = 0;
        int retried = 0;
        int dead = 0;

        while (claimed < batchSize) {
            Optional<OutboxMessage> next = outbox.claimNext(now, now.plus(visibilityTimeout));
            if (next.isEmpty()) {
                break;
            }
            OutboxMessage message = next.get();
            claimed++;

            OutboxHandler handler = handlers.get(message.topic());
            if (handler == null) {
                outbox.markDead(message.id(), now, "no handler for topic " + message.topic());
                dead++;
                log.error("outbox message {} has no handler for topic {}", message.id(), message.topic());
                continue;
            }

            try {
                handler.handle(message.payload());
                outbox.markSent(message.id(), now);
                sent++;
            } catch (OutboxPermanentFailure e) {
                outbox.markDead(message.id(), now, e.toString());
                dead++;
                log.error("outbox message {} permanently failed: {}", message.id(), e.toString());
            } catch (RuntimeException e) {
                String error = e.toString();
                if (message.attemptCount() >= maxAttempts) {
                    outbox.markDead(message.id(), now, error);
                    dead++;
                    log.error("outbox message {} dead after {} attempts: {}",
                            message.id(), message.attemptCount(), error);
                } else {
                    Instant nextAttempt = now.plus(backoffFor(message.attemptCount()));
                    outbox.reschedule(message.id(), nextAttempt, error);
                    retried++;
                    log.warn("outbox message {} attempt {} failed, retrying after {}: {}",
                            message.id(), message.attemptCount(), nextAttempt, error);
                }
            }
        }

        OutboxRunResult result = new OutboxRunResult(claimed, sent, retried, dead, released, now);
        if (claimed > 0) {
            log.info("outbox run claimed={} sent={} retried={} dead={}", claimed, sent, retried, dead);
        }
        return result;
    }

    Duration backoffFor(int attemptCount) {
        int exponent = Math.max(0, attemptCount - 1);
        if (exponent >= 32) {
            return maxBackoff;
        }
        Duration scaled = baseBackoff.multipliedBy(1L << exponent);
        return scaled.compareTo(maxBackoff) > 0 ? maxBackoff : scaled;
    }
}
