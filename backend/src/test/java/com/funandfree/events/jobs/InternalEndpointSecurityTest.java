package com.funandfree.events.jobs;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.funandfree.events.security.FirebaseAuthentication;
import com.funandfree.events.support.TestDatabase;
import com.funandfree.events.user.AppUser;
import com.funandfree.events.user.Role;
import com.funandfree.events.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalEndpointSecurityTest {

    @DynamicPropertySource
    static void sqliteOnDisk(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TestDatabase.urlFor("internal.db"));
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Test
    void anonymousCallsAreRejected() throws Exception {
        mvc.perform(post("/internal/jobs/reminders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void garbageBearerTokenIsRejected() throws Exception {
        mvc.perform(post("/internal/jobs/reminders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aSignedInAppUserCannotReachInternalJobs() throws Exception {
        long id = users.upsert("internal-prober", "prober@example.com", "Prober", Role.ORGANIZER);
        AppUser user = users.findById(id).orElseThrow();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("internal-prober").build();

        mvc.perform(post("/internal/jobs/reminders")
                        .with(authentication(new FirebaseAuthentication(user, jwt,
                                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))))
                .andExpect(status().isForbidden());
    }
}
