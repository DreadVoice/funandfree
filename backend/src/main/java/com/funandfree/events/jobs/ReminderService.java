package com.funandfree.events.jobs;

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
    private final ReminderSender sender;
    private final TransactionTemplate transactions;
    private final Duration leadTime;

    ReminderService(ReminderRepository reminders,
                    ReminderSender sender,
                    PlatformTransactionManager transactionManager,
                    @Value("${app.reminders.lead-time}") Duration leadTime) {
        this.reminders = reminders;
        this.sender = sender;
        this.transactions = new TransactionTemplate(transactionManager);
        this.leadTime = leadTime;
    }

    public ReminderRunResult run() {
        Instant now = Instant.now();
        Instant until = now.plus(leadTime);

        List<DueReminder> due = reminders.findDue(now, until);
        int sent = 0;
        int failed = 0;
        int skipped = 0;

        for (DueReminder reminder : due) {
            Boolean claimed = transactions.execute(status -> reminders.claim(reminder.eventId(), reminder.userId()));
            if (!Boolean.TRUE.equals(claimed)) {
                skipped++;
                continue;
            }
            try {
                sender.send(reminder);
                sent++;
            } catch (RuntimeException e) {
                failed++;
                transactions.executeWithoutResult(status -> reminders.release(reminder.eventId(), reminder.userId()));
                log.warn("reminder failed event={} user={}: {}", reminder.eventId(), reminder.userId(), e.toString());
            }
        }

        ReminderRunResult result = new ReminderRunResult(due.size(), sent, failed, skipped, now);
        log.info("reminder run due={} sent={} failed={} skipped={}", due.size(), sent, failed, skipped);
        return result;
    }
}
