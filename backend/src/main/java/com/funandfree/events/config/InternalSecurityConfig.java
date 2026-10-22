package com.funandfree.events.config;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration
public class InternalSecurityConfig {

    private final String issuer;
    private final String jwkSetUri;
    private final String audience;
    private final String serviceAccount;

    InternalSecurityConfig(
            @Value("${app.jobs.oidc.issuer}") String issuer,
            @Value("${app.jobs.oidc.jwk-set-uri}") String jwkSetUri,
            @Value("${app.jobs.oidc.audience}") String audience,
            @Value("${app.jobs.scheduler-service-account}") String serviceAccount) {
        this.issuer = issuer;
        this.jwkSetUri = jwkSetUri;
        this.audience = audience;
        this.serviceAccount = serviceAccount;
    }

    @Bean
    @Order(1)
    SecurityFilterChain internalFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().access(this::schedulerOnly))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(schedulerDecoder())))
                .build();
    }

    private AuthorizationDecision schedulerOnly(Supplier<Authentication> authentication,
                                                RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        if (current == null || !current.isAuthenticated()
                || !(current instanceof JwtAuthenticationToken jwtAuthentication)) {
            return new AuthorizationDecision(false);
        }
        if (serviceAccount.isBlank()) {
            return new AuthorizationDecision(true);
        }
        Jwt token = jwtAuthentication.getToken();
        return new AuthorizationDecision(serviceAccount.equals(token.getClaimAsString("email")));
    }

    private JwtDecoder schedulerDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(),
                JwtValidators.createDefaultWithIssuer(issuer),
                audienceValidator(),
                serviceAccountValidator())));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> audienceValidator() {
        return jwt -> {
            if (audience.isBlank()) {
                return OAuth2TokenValidatorResult.success();
            }
            List<String> audiences = jwt.getAudience();
            if (audiences != null && audiences.contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "unexpected audience: " + audiences, null));
        };
    }

    private OAuth2TokenValidator<Jwt> serviceAccountValidator() {
        return jwt -> {
            if (serviceAccount.isBlank()) {
                return OAuth2TokenValidatorResult.success();
            }
            String email = jwt.getClaimAsString("email");
            boolean verified = Boolean.TRUE.equals(jwt.getClaim("email_verified"));
            if (serviceAccount.equals(email) && verified) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "caller is not the scheduler service account", null));
        };
    }
}
