package com.example.dropshop.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

  private SecretKey key;

  @Value("${jwt.secret}")
  private String secret;

  private final long ACCESS_TOKEN_EXPIRE = 1000 * 60 * 30; // 30분
  private final long REFRESH_TOKEN_EXPIRE = 1000L * 60 * 60 * 24 * 7; // 7일

  @PostConstruct
  public void init() {
    key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  // AccessToken 생성
  public String createAccessToken(String email, String role) {
    return Jwts.builder()
        .subject(email)
        .claim("role", role)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRE))
        .signWith(key)
        .compact();
  }

  // RefreshToken 생성
  public String createRefreshToken(String email) {
    return Jwts.builder()
        .subject(email)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_EXPIRE))
        .signWith(key)
        .compact();
  }

  // 토큰 검증
  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  // 이메일 추출
  public String getEmail(String token) {
    return parseClaims(token).getSubject();
  }

  // role 추출
  public String getRole(String token) {
    return parseClaims(token).get("role", String.class);
  }

  // 토큰 남은 만료 시간 (밀리초)
  public long getRemainingExpiration(String token) {
    Date expiration = parseClaims(token).getExpiration();
    long remaining = expiration.getTime() - System.currentTimeMillis();
    return Math.max(remaining, 0);
  }

  // 공통 파싱 메서드
  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(key) // 🔥 SecretKey 사용
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
