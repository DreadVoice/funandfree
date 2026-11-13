package com.funandfree.events.outbox;

public record OutboxStats(int pending, int sent, int dead) {
}
