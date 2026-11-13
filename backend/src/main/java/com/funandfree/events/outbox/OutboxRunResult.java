package com.funandfree.events.outbox;

import java.time.Instant;

public record OutboxRunResult(
        int claimed,
        int sent,
        int retried,
        int dead,
        int released,
        Instant ranAt) {
}
