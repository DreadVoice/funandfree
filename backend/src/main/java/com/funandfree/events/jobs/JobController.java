package com.funandfree.events.jobs;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/jobs")
public class JobController {

    private final ReminderService reminders;

    JobController(ReminderService reminders) {
        this.reminders = reminders;
    }

    @PostMapping("/reminders")
    public ReminderRunResult runReminders() {
        return reminders.run();
    }
}
