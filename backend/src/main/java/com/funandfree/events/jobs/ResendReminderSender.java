package com.funandfree.events.jobs;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Primary
@RegisterReflectionForBinding({ResendEmailRequest.class, ResendEmailResponse.class})
public class ResendReminderSender implements ReminderSender {

    private static final Logger log = LoggerFactory.getLogger(ResendReminderSender.class);

    private final RestClient client;
    private final ReminderSender fallback;
    private final String apiKey;
    private final String from;

    ResendReminderSender(RestClient resendRestClient,
                         LoggingReminderSender fallback,
                         @Value("${app.reminders.email.api-key}") String apiKey,
                         @Value("${app.reminders.email.from}") String from) {
        this.client = resendRestClient;
        this.fallback = fallback;
        this.apiKey = apiKey;
        this.from = from;
    }

    @Override
    public void send(DueReminder reminder) {
        if (apiKey.isBlank()) {
            log.debug("no resend api key configured, logging reminder instead");
            fallback.send(reminder);
            return;
        }
        if (reminder.email() == null || reminder.email().isBlank()) {
            throw new ReminderDeliveryException("no email address for user " + reminder.userId());
        }

        ResendEmailRequest request = new ResendEmailRequest(
                from,
                List.of(reminder.email()),
                subjectFor(reminder),
                bodyFor(reminder));

        try {
            ResendEmailResponse response = client.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ResendEmailResponse.class);
            log.info("reminder sent event={} user={} message={}",
                    reminder.eventId(), reminder.userId(),
                    response == null ? "unknown" : response.id());
        } catch (RestClientException e) {
            throw new ReminderDeliveryException(
                    "resend rejected the reminder for user " + reminder.userId(), e);
        }
    }

    private String subjectFor(DueReminder reminder) {
        return "Reminder: " + reminder.eventTitle() + " is coming up";
    }

    private String bodyFor(DueReminder reminder) {
        ZoneId zone = zoneOf(reminder.timeZone());
        String when = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withZone(zone)
                .format(reminder.startsAtUtc());

        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(displayName(reminder)).append(",\n\n");
        body.append(reminder.eventTitle()).append(" starts on ").append(when)
                .append(" (").append(zone.getId()).append(").\n");
        if (reminder.venue() != null && !reminder.venue().isBlank()) {
            body.append("Venue: ").append(reminder.venue()).append("\n");
        }
        body.append("\nIf you can no longer attend, cancel your booking so the seat goes to someone on the waitlist.\n");
        return body.toString();
    }

    private String displayName(DueReminder reminder) {
        if (reminder.displayName() != null && !reminder.displayName().isBlank()) {
            return reminder.displayName();
        }
        return "there";
    }

    private ZoneId zoneOf(String timeZone) {
        try {
            return ZoneId.of(timeZone);
        } catch (RuntimeException e) {
            return ZoneId.of("UTC");
        }
    }
}
