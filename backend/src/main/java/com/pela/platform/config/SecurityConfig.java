package com.pela.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pela.shared.adapter.in.web.JsonSecurityErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    private static final String[] API_DOCS_PATHS = {"/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    /** Endpoint auth chưa có phiên (SEC-2). /v1/auth/logout vẫn cần token. */
    private static final String[] PUBLIC_AUTH_PATHS = {
            "/v1/auth/register", "/v1/auth/login", "/v1/auth/refresh", "/v1/auth/password/**"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
            @Value("${springdoc.api-docs.enabled:false}") boolean apiDocsEnabled) throws Exception {
        JsonSecurityErrorHandler errors = new JsonSecurityErrorHandler(objectMapper);
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/v1/health", "/actuator/health").permitAll();
                    auth.requestMatchers(HttpMethod.POST, PUBLIC_AUTH_PATHS).permitAll();
                    // Swagger UI chỉ mở khi springdoc bật (profile dev); ngoài dev các đường dẫn này cần token
                    if (apiDocsEnabled) {
                        auth.requestMatchers(API_DOCS_PATHS).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .build();
    }
}
