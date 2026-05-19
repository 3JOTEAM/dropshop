package com.example.dropshop.common.config;

import com.example.dropshop.common.jwt.JwtAuthenticationFilter;
import com.example.dropshop.common.jwt.JwtUtil;
import com.example.dropshop.domain.auth.service.TokenBlacklistService;
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
  private final TokenBlacklistService tokenBlacklistService;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 세션 미사용
        .authorizeHttpRequests(
            auth ->
                auth
                    // 인증 없이 접근 가능한 경로
                    .requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers("/api/auth/login", "/api/auth/refresh")
                    .permitAll()
                    .requestMatchers("/api/users/signup")
                    .permitAll()
                    // 로컬 개발/테스트 전용 (운영 배포 시 제거)
                    .requestMatchers("/api/dev/**")
                    .permitAll()
                    // static 테스트 페이지
                    .requestMatchers("/test.html", "/redirect.html")
                    .permitAll()
                    // 관리자 전용 경로
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    // 판매자 전용 경로
                    .requestMatchers("/api/sellers/drops/**")
                    .hasRole("SELLER")
                    .requestMatchers("/api/sellers/products/**")
                    .hasRole("SELLER")
                    .requestMatchers("/api/sellers/images/**")
                    .hasRole("SELLER")
                    .requestMatchers("/api/sellers/**")
                    .authenticated()
                    .requestMatchers("/api/products/**")
                    .permitAll()
                    .requestMatchers("/api/drops/**")
                    .permitAll()
                    .requestMatchers("/api/wishlists/**")
                    .permitAll()
                    .requestMatchers("/api/queues/**")
                    .permitAll()
                    .requestMatchers("/api/recommendations/**")
                    .permitAll()
                    .requestMatchers("/api/admin/sellers/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        // JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 배치
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtUtil, tokenBlacklistService),
            UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
