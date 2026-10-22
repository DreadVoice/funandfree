package com.funandfree.events.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingReminderSender implements ReminderSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingReminderSender.class);

    @Override
    public void send(DueReminder reminder) {
        log.info("reminder event={} user={} email={} starts={} zone={}",
                reminder.eventId(), reminder.userId(), reminder.email(),
                reminder.startsAtUtc(), reminder.timeZone());
    }
}
