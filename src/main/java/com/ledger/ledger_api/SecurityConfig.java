package com.ledger.ledger_api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. Enable CORS using the bean defined below
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Disable CSRF since we are using stateless JWTs
                .csrf(csrf -> csrf.disable())

                // 3. Configure endpoint routing
                .authorizeHttpRequests(auth -> auth
                        // Allow public access to static game data
                        .requestMatchers("/api/v1/killers/**", "/api/v1/perks/**", "/api/v1/addons/**").permitAll()

                        // All other requests (seasons, trials, stats) require a valid token
                        .anyRequest().authenticated()
                )

                // 4. Configure the backend as an OAuth2 Resource Server expecting JWTs
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Whitelist your frontend development ports
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:4200"));

        // Allow standard REST methods
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Allow the headers necessary for JSON payloads and Bearer tokens
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply these rules to all endpoints within the application
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
