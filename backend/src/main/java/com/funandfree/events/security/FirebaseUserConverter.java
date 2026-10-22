package com.funandfree.events.security;

import com.funandfree.events.user.AppUser;
import com.funandfree.events.user.Role;
import com.funandfree.events.user.UserRepository;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

@Component
public class FirebaseUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    FirebaseUserConverter(UserRepository users) {
        this.users = users;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String firebaseUid = jwt.getSubject();
        if (firebaseUid == null || firebaseUid.isBlank()) {
            throw new InvalidBearerTokenException("token has no subject");
        }
        AppUser user = users.findByFirebaseUid(firebaseUid)
                .orElseGet(() -> provision(jwt, firebaseUid));
        return new FirebaseAuthentication(user, jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
    }

    private AppUser provision(Jwt jwt, String firebaseUid) {
        long id = users.upsert(firebaseUid, jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"), Role.ATTENDEE);
        return users.findById(id)
                .orElseThrow(() -> new IllegalStateException("provisioned user " + id + " not found"));
    }
}
