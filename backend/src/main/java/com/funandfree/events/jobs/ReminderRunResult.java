package com.funandfree.events.jobs;

import java.time.Instant;

public record ReminderRunResult(
        int due,
        int sent,
        int failed,
        int skipped,
        Instant ranAt) {
}
