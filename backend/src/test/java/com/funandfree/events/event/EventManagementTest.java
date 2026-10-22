package com.funandfree.events.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.funandfree.events.booking.BookingOutcome;
import com.funandfree.events.booking.BookingService;
import com.funandfree.events.security.FirebaseAuthentication;
import com.funandfree.events.support.TestDatabase;
import com.funandfree.events.user.AppUser;
import com.funandfree.events.user.Role;
import com.funandfree.events.user.UserRepository;
import com.funandfree.events.waitlist.WaitlistRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventManagementTest {

    @DynamicPropertySource
    static void sqliteOnDisk(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TestDatabase.urlFor("management.db"));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    UserRepository users;

    @Autowired
    EventRepository events;

    @Autowired
    BookingService bookings;

    @Autowired
    WaitlistRepository waitlist;

    private Authentication as(String uid, Role role) {
        long id = users.upsert(uid, uid + "@example.com", uid, role);
        AppUser user = users.findById(id).orElseThrow();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(uid).build();
        return new FirebaseAuthentication(user, jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private Map<String, Object> payload(String title, int capacity, Instant startsAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("description", "an evening of live music");
        body.put("venue", "Pier 7");
        body.put("startsAtUtc", startsAt.toString());
        body.put("timeZone", "America/Chicago");
        body.put("capacity", capacity);
        return body;
    }

    private Instant soon() {
        return Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    }

    private String body(Map<String, Object> value) throws Exception {
        return json.writeValueAsString(value);
    }

    @Test
    void organizerCreatesAnEvent() throws Exception {
        mvc.perform(post("/api/events")
                        .with(authentication(as("creator-organizer", Role.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload("Created Event", 25, soon()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Created Event")))
                .andExpect(jsonPath("$.capacity", is(25)))
                .andExpect(jsonPath("$.seatsRemaining", is(25)))
                .andExpect(jsonPath("$.status", is("PUBLISHED")));
    }

    @Test
    void attendeeCannotCreateAnEvent() throws Exception {
        mvc.perform(post("/api/events")
                        .with(authentication(as("creator-attendee", Role.ATTENDEE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload("Forbidden Event", 10, soon()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void creatingRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload("Anonymous Event", 10, soon()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankTitleAndZeroCapacityAreRejected() throws Exception {
        Map<String, Object> invalid = payload("", 0, soon());
        mvc.perform(post("/api/events")
                        .with(authentication(as("validation-organizer", Role.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("capacity")))
                .andExpect(jsonPath("$.message", containsString("title")));
    }

    @Test
    void pastStartTimeIsRejected() throws Exception {
        Map<String, Object> invalid = payload("Past Event", 5,
                Instant.now().minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS));
        mvc.perform(post("/api/events")
                        .with(authentication(as("past-organizer", Role.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("startsAtUtc")));
    }

    @Test
    void unknownTimeZoneIsRejected() throws Exception {
        Map<String, Object> invalid = payload("Bad Zone", 5, soon());
        invalid.put("timeZone", "Mars/Olympus_Mons");
        mvc.perform(post("/api/events")
                        .with(authentication(as("zone-organizer", Role.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(invalid)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Mars/Olympus_Mons")));
    }

    @Test
    void endTimeMustFollowStartTime() throws Exception {
        Instant startsAt = soon();
        Map<String, Object> invalid = payload("Backwards", 5, startsAt);
        invalid.put("endsAtUtc", startsAt.minus(1, ChronoUnit.HOURS).toString());
        mvc.perform(post("/api/events")
                        .with(authentication(as("order-organizer", Role.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(invalid)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("endsAtUtc")));
    }

    @Test
    void organizerEditsOwnEvent() throws Exception {
        Authentication organizer = as("editor-organizer", Role.ORGANIZER);
        AppUser user = users.findByFirebaseUid("editor-organizer").orElseThrow();
        long eventId = events.insert(user.id(), "Before Edit", "desc", "Old Venue",
                soon(), "UTC", 10);

        Map<String, Object> update = payload("After Edit", 40, soon());
        update.put("venue", "New Venue");

        mvc.perform(put("/api/events/{id}", eventId)
                        .with(authentication(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("After Edit")))
                .andExpect(jsonPath("$.venue", is("New Venue")))
                .andExpect(jsonPath("$.capacity", is(40)));
    }

    @Test
    void anotherOrganizerCannotEditSomeoneElsesEvent() throws Exception {
        AppUser owner = users.findById(users.upsert("owner-organizer", "o@example.com", "Owner", Role.ORGANIZER))
                .orElseThrow();
        long eventId = events.insert(owner.id(), "Owned Event", "desc", "Venue", soon(), "UTC", 10);

        mvc.perform(put("/api/events/{id}", eventId)
                        .with(authentication(as("intruder-organizer", Role.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload("Hijacked", 10, soon()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void editingAnUnknownEventReturns404() throws Exception {
        mvc.perform(put("/api/events/{id}", 876543L)
                        .with(authentication(as("ghost-organizer", Role.ORGANIZER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload("Nowhere", 10, soon()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void capacityCannotDropBelowSeatsTaken() throws Exception {
        Authentication organizer = as("shrink-organizer", Role.ORGANIZER);
        AppUser owner = users.findByFirebaseUid("shrink-organizer").orElseThrow();
        long eventId = events.insert(owner.id(), "Shrinking Event", "desc", "Venue", soon(), "UTC", 3);

        bookings.book(eventId, users.upsert("shrink-a", "a@example.com", "A", Role.ATTENDEE));
        bookings.book(eventId, users.upsert("shrink-b", "b@example.com", "B", Role.ATTENDEE));

        mvc.perform(put("/api/events/{id}", eventId)
                        .with(authentication(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload("Shrinking Event", 1, soon()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("below")));

        mvc.perform(get("/api/events/{id}", eventId))
                .andExpect(jsonPath("$.capacity", is(3)));
    }

    @Test
    void raisingCapacityPromotesWaitlistedAttendees() throws Exception {
        Authentication organizer = as("growth-organizer", Role.ORGANIZER);
        AppUser owner = users.findByFirebaseUid("growth-organizer").orElseThrow();
        long eventId = events.insert(owner.id(), "Growing Event", "desc", "Venue", soon(), "UTC", 1);

        bookings.book(eventId, users.upsert("growth-a", "ga@example.com", "GA", Role.ATTENDEE));
        BookingOutcome second = bookings.book(eventId, users.upsert("growth-b", "gb@example.com", "GB", Role.ATTENDEE));
        BookingOutcome third = bookings.book(eventId, users.upsert("growth-c", "gc@example.com", "GC", Role.ATTENDEE));
        assertThat(second).isEqualTo(BookingOutcome.WAITLISTED);
        assertThat(third).isEqualTo(BookingOutcome.WAITLISTED);

        mvc.perform(put("/api/events/{id}", eventId)
                        .with(authentication(organizer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload("Growing Event", 3, soon()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity", is(3)))
                .andExpect(jsonPath("$.seatsTaken", is(3)))
                .andExpect(jsonPath("$.seatsRemaining", is(0)));

        assertThat(waitlist.countWaiting(eventId)).isZero();
    }

    @Test
    void organizerListingShowsOnlyTheirOwnEvents() throws Exception {
        Authentication mine = as("listing-organizer", Role.ORGANIZER);
        AppUser owner = users.findByFirebaseUid("listing-organizer").orElseThrow();
        AppUser other = users.findById(users.upsert("other-listing", "ol@example.com", "OL", Role.ORGANIZER))
                .orElseThrow();

        events.insert(owner.id(), "My Listed Event", "desc", "Venue", soon(), "UTC", 5);
        events.insert(other.id(), "Their Listed Event", "desc", "Venue", soon(), "UTC", 5);

        mvc.perform(get("/api/events/mine").with(authentication(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title", is("My Listed Event")))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void organizerListingIsClosedToAttendees() throws Exception {
        mvc.perform(get("/api/events/mine").with(authentication(as("listing-attendee", Role.ATTENDEE))))
                .andExpect(status().isForbidden());
    }
}
