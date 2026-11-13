package com.funandfree.events.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.funandfree.events.outbox.OutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final ReminderRepository reminders;
    private final OutboxRepository outbox;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final Duration leadTime;

    ReminderService(ReminderRepository reminders,
                    OutboxRepository outbox,
                    ObjectMapper json,
                    PlatformTransactionManager transactionManager,
                    @Value("${app.reminders.lead-time}") Duration leadTime) {
        this.reminders = reminders;
        this.outbox = outbox;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
        this.leadTime = leadTime;
    }

    public ReminderRunResult run() {
        Instant now = Instant.now();
        Instant until = now.plus(leadTime);

        List<DueReminder> due = reminders.findDue(now, until);
        int enqueued = 0;
        int skipped = 0;

        for (DueReminder reminder : due) {
            Boolean accepted = transactions.execute(status -> {
                if (!reminders.claim(reminder.eventId(), reminder.userId())) {
                    return false;
                }
                outbox.enqueue(
                        ReminderOutboxHandler.TOPIC,
                        payloadFor(reminder),
                        dedupKeyFor(reminder));
                return true;
            });
            if (Boolean.TRUE.equals(accepted)) {
                enqueued++;
            } else {
                skipped++;
            }
        }

        ReminderRunResult result = new ReminderRunResult(due.size(), enqueued, skipped, now);
        log.info("reminder run due={} enqueued={} skipped={}", due.size(), enqueued, skipped);
        return result;
    }

    private String payloadFor(DueReminder reminder) {
        try {
            return json.writeValueAsString(new ReminderPayload(reminder.eventId(), reminder.userId()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("could not serialise reminder payload", e);
        }
    }

    private String dedupKeyFor(DueReminder reminder) {
        return ReminderOutboxHandler.TOPIC + ":" + reminder.eventId() + ":" + reminder.userId();
    }
}
