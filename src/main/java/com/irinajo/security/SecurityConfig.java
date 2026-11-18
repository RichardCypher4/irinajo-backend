package com.irinajo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irinajo.common.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper mapper;

    public SecurityConfig(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)            // stateless token API, no cookies
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())                      // only has an effect on dev/staging (CorsConfig)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/t/**").permitAll()                 // public Walk Safe tracking page
                        .requestMatchers("/account/delete", "/account/delete/**").permitAll() // Google Play web deletion page
                        .requestMatchers("/robots.txt", "/error").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/content/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/walks/nearby", "/walks/search").permitAll()
                        .requestMatchers(HttpMethod.POST, "/routes/suggest").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(tokenResolver())
                        .jwt(j -> j.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthConverter()))
                        .authenticationEntryPoint(entryPoint()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint())
                        .accessDeniedHandler(deniedHandler()))
                .headers(h -> h
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000))
                        .frameOptions(f -> f.deny())
                        .contentTypeOptions(c -> {})
                        .referrerPolicy(r -> r.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    /** Ignore stale bearer tokens on public sign-in endpoints so an expired token never blocks signing in. */
    private BearerTokenResolver tokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return req -> {
            String path = req.getRequestURI();
            if (path.startsWith("/auth/") || path.startsWith("/t/")
                    || path.startsWith("/account/delete")) return null;
            return delegate.resolve(req);
        };
    }

    private JwtAuthenticationConverter jwtAuthConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(roles);
        return c;
    }

    private AuthenticationEntryPoint entryPoint() {
        return (req, res, ex) -> write(res, HttpServletResponse.SC_UNAUTHORIZED,
                GlobalExceptionHandler.body("UNAUTHORIZED", "Sign in again or refresh your token", false));
    }

    private AccessDeniedHandler deniedHandler() {
        return (req, res, ex) -> write(res, HttpServletResponse.SC_FORBIDDEN,
                GlobalExceptionHandler.body("FORBIDDEN", "Not allowed", false));
    }

    private void write(HttpServletResponse res, int status, Map<String, Object> body) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), body);
    }
}