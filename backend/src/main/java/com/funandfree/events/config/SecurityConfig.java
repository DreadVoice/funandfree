package com.funandfree.events.config;

import com.funandfree.events.security.FirebaseUserConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    FirebaseUserConverter firebaseUserConverter,
                                    CorsConfigurationSource corsConfigurationSource) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events/mine").hasRole("ORGANIZER")
                        .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events/{eventId}").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/events").hasRole("ORGANIZER")
                        .requestMatchers(HttpMethod.PUT, "/api/events/{eventId}").hasRole("ORGANIZER")
                        .requestMatchers("/internal/**").denyAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(firebaseUserConverter)))
                .build();
    }
}
