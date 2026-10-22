package com.funandfree.events.jobs;

public class ReminderDeliveryException extends RuntimeException {

    public ReminderDeliveryException(String message) {
        super(message);
    }

    public ReminderDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
