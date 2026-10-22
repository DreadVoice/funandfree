package com.funandfree.events.waitlist;

import java.time.Instant;

public record WaitlistEntry(
        long id,
        long eventId,
        long userId,
        String status,
        Instant createdAt,
        Instant promotedAt) {
}
