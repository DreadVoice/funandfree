package com.funandfree.events.booking;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funandfree.events.event.EventRepository;
import com.funandfree.events.security.FirebaseAuthentication;
import com.funandfree.events.support.TestDatabase;
import com.funandfree.events.user.AppUser;
import com.funandfree.events.user.Role;
import com.funandfree.events.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
class BookingEndpointsTest {

    @DynamicPropertySource
    static void sqliteOnDisk(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TestDatabase.urlFor("endpoints.db"));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    EventRepository events;

    @Autowired
    UserRepository users;

    private Authentication as(String uid) {
        long id = users.upsert(uid, uid + "@example.com", uid, Role.ATTENDEE);
        AppUser user = users.findById(id).orElseThrow();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(uid)
                .claim("email", user.email())
                .build();
        return new FirebaseAuthentication(user, jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
    }

    private long newEvent(String title, int capacity) {
        long organizerId = users.upsert("organizer-" + title, "o@example.com", "Org", Role.ORGANIZER);
        return events.insert(organizerId, title, "live music", "Venue",
                Instant.now().plus(9, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS), "UTC", capacity);
    }

    @Test
    void bookingRequiresAuthentication() throws Exception {
        long eventId = newEvent("Auth Required", 5);
        mvc.perform(post("/api/events/{id}/bookings", eventId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingMyBookingsRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/bookings/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsStayOpen() throws Exception {
        long eventId = newEvent("Public Detail", 3);
        mvc.perform(get("/api/events")).andExpect(status().isOk());
        mvc.perform(get("/api/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Public Detail")));
        mvc.perform(get("/api/events/search").param("q", "music")).andExpect(status().isOk());
    }

    @Test
    void unknownEventDetailReturns404() throws Exception {
        mvc.perform(get("/api/events/{id}", 987654L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)));
    }

    @Test
    void bookingConfirmsThenConflictsOnRepeat() throws Exception {
        long eventId = newEvent("Repeat Booking", 5);
        Authentication user = as("repeat-booker");

        mvc.perform(post("/api/events/{id}/bookings", eventId).with(authentication(user)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome", is("CONFIRMED")));

        mvc.perform(post("/api/events/{id}/bookings", eventId).with(authentication(user)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    void bookingAFullEventReturnsAccepted() throws Exception {
        long eventId = newEvent("Full Event", 1);

        mvc.perform(post("/api/events/{id}/bookings", eventId).with(authentication(as("full-holder"))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/events/{id}/bookings", eventId).with(authentication(as("full-waiter"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome", is("WAITLISTED")));
    }

    @Test
    void cancellingReportsTheTransferredSeat() throws Exception {
        long eventId = newEvent("Transfer Endpoint", 1);
        Authentication holder = as("endpoint-holder");
        Authentication waiter = as("endpoint-waiter");

        mvc.perform(post("/api/events/{id}/bookings", eventId).with(authentication(holder)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/events/{id}/bookings", eventId).with(authentication(waiter)))
                .andExpect(status().isAccepted());

        mvc.perform(delete("/api/events/{id}/bookings/me", eventId).with(authentication(holder)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatTransferred", is(true)));

        mvc.perform(get("/api/bookings/me").with(authentication(waiter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status", is("CONFIRMED")));
    }

    @Test
    void cancellingWithoutABookingReturns404() throws Exception {
        long eventId = newEvent("No Booking Endpoint", 2);
        mvc.perform(delete("/api/events/{id}/bookings/me", eventId).with(authentication(as("nobody"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void creatingEventsRequiresOrganizerRole() throws Exception {
        mvc.perform(post("/api/events").with(authentication(as("mere-attendee"))))
                .andExpect(status().isForbidden());
    }
}
