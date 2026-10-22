package com.funandfree.events.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funandfree.events.support.TestDatabase;
import com.funandfree.events.user.AppUser;
import com.funandfree.events.user.Role;
import com.funandfree.events.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class FirebaseUserConverterTest {

    @DynamicPropertySource
    static void sqliteOnDisk(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TestDatabase.urlFor("converter.db"));
    }

    @Autowired
    FirebaseUserConverter converter;

    @Autowired
    UserRepository users;

    private Jwt tokenFor(String uid, String email, String name) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject(uid)
                .claim("email", email)
                .claim("name", name)
                .build();
    }

    @Test
    void unknownSubjectIsProvisionedAsAttendee() {
        AbstractAuthenticationToken authentication =
                converter.convert(tokenFor("brand-new-uid", "new@example.com", "New Person"));

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo("brand-new-uid");
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ATTENDEE");

        AppUser principal = (AppUser) authentication.getPrincipal();
        assertThat(principal.role()).isEqualTo(Role.ATTENDEE);
        assertThat(principal.email()).isEqualTo("new@example.com");
        assertThat(principal.displayName()).isEqualTo("New Person");
    }

    @Test
    void existingOrganizerKeepsTheRoleFromTheDatabase() {
        users.upsert("known-organizer", "org@example.com", "Org Person", Role.ORGANIZER);

        AbstractAuthenticationToken authentication =
                converter.convert(tokenFor("known-organizer", "org@example.com", "Org Person"));

        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ORGANIZER");
        assertThat(((AppUser) authentication.getPrincipal()).isOrganizer()).isTrue();
    }

    @Test
    void repeatedConversionsReuseTheSameUserRow() {
        AbstractAuthenticationToken first = converter.convert(tokenFor("stable-uid", "s@example.com", "S"));
        AbstractAuthenticationToken second = converter.convert(tokenFor("stable-uid", "s@example.com", "S"));

        long firstId = ((AppUser) first.getPrincipal()).id();
        long secondId = ((AppUser) second.getPrincipal()).id();
        assertThat(firstId).isEqualTo(secondId);
    }

    @Test
    void tokenWithoutSubjectIsRejected() {
        Jwt withoutSubject = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("email", "anonymous@example.com")
                .build();

        assertThatThrownBy(() -> converter.convert(withoutSubject))
                .isInstanceOf(InvalidBearerTokenException.class);
    }
}
