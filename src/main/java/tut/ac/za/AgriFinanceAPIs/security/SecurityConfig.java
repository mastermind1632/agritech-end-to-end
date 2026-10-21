package tut.ac.za.AgriFinanceAPIs.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * The JWT filter is created here (not declared as a @Component/@Bean) on purpose.
     * A filter bean is ALSO auto-registered by Spring Boot as a plain servlet filter,
     * so it would run outside the security chain and be created too early during
     * Tomcat start-up (which is where the original startup crash surfaced).
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService);

        http.csrf(csrf -> csrf.disable())
            .cors(cors -> { })
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Missing/invalid/expired token -> 401 (Spring's default would be a confusing 403)
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/farmers/register", "/api/farmers/login", "/error").permitAll()
                .requestMatchers("/api/auth/refresh", "/api/auth/logout", "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                // Live notifications: EventSource cannot send headers, so the stream checks its own short-lived ticket.
                .requestMatchers(HttpMethod.GET, "/api/live/stream").permitAll()
                // Public reference data (export requirements directory, guidance content, resource links).
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                // API documentation (Swagger UI). Set SPRINGDOC_ENABLED=false to switch it off in production.
                .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // Public browsing. "/api/group-orders/*" matches ONE path segment only, so
                // /api/group-orders/{id}/items (which lists farmer ids) still needs a login.
                .requestMatchers(HttpMethod.GET, "/api/suppliers", "/api/suppliers/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/group-orders", "/api/group-orders/*").permitAll()
                // Product photos are public so a plain <img> tag can load them (it cannot send a login header).
                .requestMatchers(HttpMethod.GET, "/api/market/images/*").permitAll()
                .anyRequest().authenticated())
            .headers(h -> h
                .contentTypeOptions(c -> { })
                .frameOptions(f -> f.deny())
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String[] allowedOrigins) {
        CorsConfiguration c = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins).map(String::trim).filter(s -> !s.isEmpty()).toList();
        c.setAllowedOrigins(origins);
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        c.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }
}
