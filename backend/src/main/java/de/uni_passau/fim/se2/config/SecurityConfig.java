/*
 * Copyright (C) 2019-2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.se2.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${CORS_ALLOWED_ORIGINS:*}")
    private String corsAllowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Profile endpoints: require authentication (before permitAll on /api/auth/**)
                        .requestMatchers(HttpMethod.PUT, "/api/auth/profile/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/auth/profile").authenticated()

                        // Admin endpoints: require ADMIN role
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Auth endpoints: open
                        .requestMatchers("/api/auth/**").permitAll()

                        // My-resources: require authentication (before public read)
                        .requestMatchers(HttpMethod.GET, "/api/worksheets/my").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/sequences/my").authenticated()

                        // Task mutations: require authentication (ownership checked in service)
                        .requestMatchers(HttpMethod.PUT, "/api/tasks/**").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/tasks/**").authenticated()

                        // AP15b: Distractor generation endpoints
                        .requestMatchers(HttpMethod.POST, "/api/tasks/*/generate-distractors").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/sequence/*/generate-distractors").authenticated()

                        // Public read endpoints: anyone can view via link
                        .requestMatchers(HttpMethod.GET, "/api/sequence/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/sequences/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tasks/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/worksheets/**").permitAll()

                        // Task generation: permitAll at HTTP level, student-check in controller
                        .requestMatchers(HttpMethod.POST, "/api/tasks/generate").permitAll()

                        // Feedback logging: open (anonymous feedback allowed)
                        .requestMatchers(HttpMethod.POST, "/api/tasks/*/feedback-log").permitAll()

                        // Learning events (AP10): POST is public, export is admin, rest is authenticated
                        .requestMatchers(HttpMethod.POST, "/api/events").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events/export").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/events/**").authenticated()

                        // Hints (AP13): public (anonymous students allowed)
                        .requestMatchers(HttpMethod.POST, "/api/hints/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/hints/**").permitAll()

                        // Change Analysis (AP12): analysis endpoints require auth, batch/export/summary admin
                        .requestMatchers(HttpMethod.POST, "/api/analysis/batch").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/analysis/export").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/analysis/summary").hasRole("ADMIN")
                        .requestMatchers("/api/analysis/**").authenticated()

                        // Worksheet mutations: require authentication (ownership checked in controller)
                        .requestMatchers(HttpMethod.POST, "/api/worksheets/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/worksheets/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/worksheets/**").authenticated()

                        // Classes: all operations require authentication (role checked in service)
                        .requestMatchers("/api/classes/**").authenticated()

                        // Swagger/OpenAPI: open
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Actuator: open
                        .requestMatchers("/actuator/**").permitAll()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        if ("*".equals(corsAllowedOrigins)) {
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            configuration.setAllowedOrigins(
                    Arrays.asList(corsAllowedOrigins.split(","))
            );
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
