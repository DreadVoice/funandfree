package com.funandfree.events.jobs;

import java.time.Instant;

public record DueReminder(
        long eventId,
        String eventTitle,
        Instant startsAtUtc,
        String timeZone,
        String venue,
        long userId,
        String email,
        String displayName) {
}
