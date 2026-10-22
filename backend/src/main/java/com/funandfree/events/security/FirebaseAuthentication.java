package com.funandfree.events.security;

import com.funandfree.events.user.AppUser;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class FirebaseAuthentication extends AbstractAuthenticationToken {

    private final transient AppUser user;
    private final transient Jwt token;

    public FirebaseAuthentication(AppUser user, Jwt token, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.user = user;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public AppUser getPrincipal() {
        return user;
    }

    @Override
    public Jwt getCredentials() {
        return token;
    }

    @Override
    public String getName() {
        return user.firebaseUid();
    }
}
