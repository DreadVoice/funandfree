package com.funandfree.events.jobs;

public interface ReminderSender {

    void send(DueReminder reminder);
}
