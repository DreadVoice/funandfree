package com.funandfree.events.jobs;

import java.time.Instant;

public record ReminderRunResult(int due, int enqueued, int skipped, Instant ranAt) {
}
