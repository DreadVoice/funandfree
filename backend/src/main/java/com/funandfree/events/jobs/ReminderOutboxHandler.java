package com.funandfree.events.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funandfree.events.outbox.OutboxHandler;
import com.funandfree.events.outbox.OutboxPermanentFailure;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Component;

@Component
@RegisterReflectionForBinding(ReminderPayload.class)
public class ReminderOutboxHandler implements OutboxHandler {

    public static final String TOPIC = "reminder.email";

    private static final Logger log = LoggerFactory.getLogger(ReminderOutboxHandler.class);

    private final ReminderRepository reminders;
    private final ReminderSender sender;
    private final ObjectMapper json;

    ReminderOutboxHandler(ReminderRepository reminders, ReminderSender sender, ObjectMapper json) {
        this.reminders = reminders;
        this.sender = sender;
        this.json = json;
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public void handle(String payload) {
        ReminderPayload target = parse(payload);
        Optional<DueReminder> reminder = reminders.findForDelivery(target.eventId(), target.userId());
        if (reminder.isEmpty()) {
            log.info("reminder for event={} user={} no longer deliverable, dropping",
                    target.eventId(), target.userId());
            return;
        }
        sender.send(reminder.get());
    }

    private ReminderPayload parse(String payload) {
        try {
            return json.readValue(payload, ReminderPayload.class);
        } catch (JsonProcessingException e) {
            throw new OutboxPermanentFailure("unreadable reminder payload: " + payload, e);
        }
    }
}
