package com.funandfree.events.jobs;

import com.funandfree.events.outbox.OutboxRelay;
import com.funandfree.events.outbox.OutboxRunResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/jobs")
public class JobController {

    private final ReminderService reminders;
    private final OutboxRelay outbox;

    JobController(ReminderService reminders, OutboxRelay outbox) {
        this.reminders = reminders;
        this.outbox = outbox;
    }

    @PostMapping("/reminders")
    public ReminderRunResult runReminders() {
        return reminders.run();
    }

    @PostMapping("/outbox")
    public OutboxRunResult runOutbox() {
        return outbox.run();
    }
}
