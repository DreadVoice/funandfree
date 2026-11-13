package com.funandfree.events.outbox;

public interface OutboxHandler {

    String topic();

    void handle(String payload);
}
