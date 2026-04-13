package com.ledger.ledger_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 1. Enable CORS using our custom rule set below (Allows React to talk to Spring)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Disable CSRF (Safe to do because we are using stateless JWTs, not browser session cookies)
                .csrf(csrf -> csrf.disable())

                // 3. Make our REST API strictly stateless (Best practice for performance)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. Configure our Authorization Rules
                .authorizeHttpRequests(auth -> auth
                        // The reference data (Killers, Perks, Addons) is completely public
                        .requestMatchers("/api/v1/reference-data/**").permitAll()
                        // EVERYTHING else (syncing players, starting seasons) requires a valid Google token
                        .anyRequest().authenticated()
                )

                // 5. Tell Spring Security to act as an OAuth2 Resource Server and look for a JWT
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))

                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Explicitly whitelist your Vite frontend port
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));

        // Allow standard REST methods + OPTIONS (OPTIONS is required for browsers to do pre-flight checks)
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Allow the specific headers we need (Authorization for the token, Content-Type for JSON)
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        // Allow credentials (often required by Axios on the frontend)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply these rules globally to every endpoint in our application
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
