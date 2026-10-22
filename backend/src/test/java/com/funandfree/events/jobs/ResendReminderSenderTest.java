package com.funandfree.events.jobs;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendReminderSenderTest {

    private static final DueReminder REMINDER = new DueReminder(
            7L, "Jazz Night at the Pier", Instant.parse("2026-11-01T19:00:00Z"),
            "America/Chicago", "Pier 7", 42L, "attendee@example.com", "Alex");

    private record Harness(ResendReminderSender sender, MockRestServiceServer server) {
    }

    private Harness harness(String apiKey) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResendReminderSender sender = new ResendReminderSender(
                builder.baseUrl("https://api.resend.com").build(), new LoggingReminderSender(),
                apiKey, "Events <events@example.com>");
        return new Harness(sender, server);
    }

    @Test
    void postsTheReminderToResend() {
        Harness harness = harness("re_test_key");
        harness.server()
                .expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.from").value("Events <events@example.com>"))
                .andExpect(jsonPath("$.to[0]").value("attendee@example.com"))
                .andExpect(jsonPath("$.subject").value("Reminder: Jazz Night at the Pier is coming up"))
                .andRespond(withSuccess("{\"id\":\"msg_123\"}", MediaType.APPLICATION_JSON));

        harness.sender().send(REMINDER);

        harness.server().verify();
    }

    @Test
    void bodyNamesTheEventVenueAndLocalTime() {
        Harness harness = harness("re_test_key");
        harness.server()
                .expect(requestTo("https://api.resend.com/emails"))
                .andExpect(jsonPath("$.text").value(allOf(
                        containsString("Alex"),
                        containsString("Jazz Night at the Pier"),
                        containsString("Pier 7"),
                        containsString("America/Chicago"))))
                .andRespond(withSuccess("{\"id\":\"msg_124\"}", MediaType.APPLICATION_JSON));

        harness.sender().send(REMINDER);

        harness.server().verify();
    }

    @Test
    void serverErrorsBecomeDeliveryFailures() {
        Harness harness = harness("re_test_key");
        harness.server()
                .expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> harness.sender().send(REMINDER))
                .isInstanceOf(ReminderDeliveryException.class)
                .hasMessageContaining("42");
    }

    @Test
    void withoutAnApiKeyNoRequestIsMade() {
        Harness harness = harness("");

        assertThatCode(() -> harness.sender().send(REMINDER)).doesNotThrowAnyException();

        harness.server().verify();
    }

    @Test
    void missingEmailAddressIsADeliveryFailure() {
        Harness harness = harness("re_test_key");
        DueReminder withoutEmail = new DueReminder(
                7L, "Jazz Night", Instant.parse("2026-11-01T19:00:00Z"),
                "UTC", "Pier 7", 43L, null, "Sam");

        assertThatThrownBy(() -> harness.sender().send(withoutEmail))
                .isInstanceOf(ReminderDeliveryException.class);

        harness.server().verify();
    }

    @Test
    void unknownTimeZoneFallsBackToUtc() {
        Harness harness = harness("re_test_key");
        DueReminder badZone = new DueReminder(
                8L, "Mystery Event", Instant.parse("2026-11-01T19:00:00Z"),
                "Mars/Olympus_Mons", null, 44L, "mars@example.com", null);
        harness.server()
                .expect(requestTo("https://api.resend.com/emails"))
                .andExpect(jsonPath("$.text").value(allOf(
                        containsString("UTC"),
                        containsString("there"))))
                .andRespond(withSuccess("{\"id\":\"msg_125\"}", MediaType.APPLICATION_JSON));

        harness.sender().send(badZone);

        harness.server().verify();
    }
}
