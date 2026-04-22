package com.example.dropshop.common.config;

import com.example.dropshop.common.jwt.JwtAuthenticationFilter;
import com.example.dropshop.common.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 미사용
                .authorizeHttpRequests(auth -> auth
                        // 인증 없이 접근 가능한 경로
                        .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/users/signup").permitAll()
                        .requestMatchers("/api/sellers/drops/**").hasRole("SELLER")
                        .requestMatchers("/api/sellers/products/**").hasRole("SELLER")
                        .requestMatchers("/api/sellers/images/**").hasRole("SELLER")
                        .requestMatchers("/api/sellers/**").permitAll() // TODO: JWT 구현 후 제거
                        .requestMatchers("/api/products/**").permitAll() // TODO: JWT 구현 후 제거
                        .requestMatchers("/payments/**").permitAll()
                        .requestMatchers("/api/payments/**").permitAll()
                        .anyRequest().authenticated()
                )
                // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 배치
                .addFilterBefore(new JwtAuthenticationFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
