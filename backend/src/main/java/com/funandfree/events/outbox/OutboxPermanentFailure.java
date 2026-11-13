package com.funandfree.events.outbox;

public class OutboxPermanentFailure extends RuntimeException {

    public OutboxPermanentFailure(String message) {
        super(message);
    }

    public OutboxPermanentFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
