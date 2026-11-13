package com.funandfree.events.outbox;

public record OutboxMessage(long id, String topic, String payload, int attemptCount) {
}
