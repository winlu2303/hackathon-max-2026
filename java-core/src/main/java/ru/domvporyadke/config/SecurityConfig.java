package ru.domvporyadke.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Value("${app.security.test-tokens.resident}")
    private String residentToken;
    @Value("${app.security.test-tokens.dispatcher}")
    private String dispatcherToken;
    @Value("${app.security.test-tokens.master}")
    private String masterToken;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/health", "/actuator/**").permitAll()
                .requestMatchers("/api/v1/buildings/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(tokenFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private OncePerRequestFilter tokenFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                String header = req.getHeader("Authorization");
                if (header != null && header.startsWith("Bearer ")) {
                    String token = header.substring(7).trim();
                    String role = resolveRole(token);
                    if (role != null) {
                        var auth = new UsernamePasswordAuthenticationToken(
                            "user", null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
                chain.doFilter(req, res);
            }

            private String resolveRole(String token) {
                if (token.equals(residentToken)) return "RESIDENT";
                if (token.equals(dispatcherToken)) return "DISPATCHER";
                if (token.equals(masterToken)) return "MASTER";
                return null;
            }
        };
    }
}