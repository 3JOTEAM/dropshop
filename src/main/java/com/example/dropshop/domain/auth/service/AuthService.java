package com.example.dropshop.domain.auth.service;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.jwt.JwtUtil;
import com.example.dropshop.common.kafka.producer.EventKafkaProducer;
import com.example.dropshop.domain.auth.dto.request.LoginRequest;
import com.example.dropshop.domain.auth.dto.response.TokenResponse;
import com.example.dropshop.domain.auth.entity.RefreshToken;
import com.example.dropshop.domain.auth.event.UserLoginEvent;
import com.example.dropshop.domain.auth.exception.AuthException;
import com.example.dropshop.domain.auth.repository.RefreshTokenRepository;
import com.example.dropshop.domain.auth.sse.service.SseEmitterService;
import com.example.dropshop.domain.user.entity.User;
import com.example.dropshop.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtUtil jwtUtil;
  private final PasswordEncoder passwordEncoder;
  private final TokenBlacklistService tokenBlacklistService;
  private final EventKafkaProducer eventKafkaProducer;
  private final SseEmitterService sseEmitterService;

  @Transactional
  public TokenResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.getEmail())
            .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));

    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new AuthException(ErrorCode.INVALID_CREDENTIALS);
    }

    String accessToken = jwtUtil.createAccessToken(user.getEmail(), user.getRole().name());
    String refreshToken = jwtUtil.createRefreshToken(user.getEmail());

    // ✅ BCrypt 72바이트 제한 해결: SHA-256 해싱 적용
    // 기존 토큰이 있으면 update, 없으면 insert (upsert 패턴)
    // delete+save 대신 사용해 unique 제약 위반 및 동시성 문제 방지
    String hashedToken = hashToken(refreshToken);
    refreshTokenRepository
        .findByEmail(user.getEmail())
        .ifPresentOrElse(
            existing -> {
              // 기존 세션이 있으면 SSE로 강제 로그아웃 알림 후 토큰 교체
              sseEmitterService.sendForceLogout(user.getEmail());
              existing.updateToken(hashedToken);
            },
            () -> refreshTokenRepository.save(new RefreshToken(user.getEmail(), hashedToken)));

    // Kafka 로그인 이벤트 발행
    eventKafkaProducer.publishUserLogin(UserLoginEvent.of(user.getEmail()));

    return new TokenResponse(accessToken, refreshToken);
  }

  @Transactional
  public String refresh(String refreshToken) {
    if (!jwtUtil.validateToken(refreshToken)) {
      throw new AuthException(ErrorCode.INVALID_TOKEN);
    }

    String email = jwtUtil.getEmail(refreshToken);
    RefreshToken savedToken =
        refreshTokenRepository
            .findByEmail(email)
            .orElseThrow(() -> new AuthException(ErrorCode.TOKEN_NOT_FOUND));

    if (!savedToken.getHashedToken().equals(hashToken(refreshToken))) {
      throw new AuthException(ErrorCode.TOKEN_MISMATCH);
    }

    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new AuthException(ErrorCode.INVALID_CREDENTIALS));

    return jwtUtil.createAccessToken(email, user.getRole().name());
  }

  /** 로그아웃 처리. - 액세스 토큰 → Redis 블랙리스트 등록 (남은 만료 시간 동안 유지) - 리프레시 토큰 → DB에서 삭제 */
  @Transactional
  public void logout(String accessToken) {
    // 액세스 토큰 블랙리스트 등록
    long remaining = jwtUtil.getRemainingExpiration(accessToken);
    tokenBlacklistService.blacklist(accessToken, remaining);

    // 리프레시 토큰 DB에서 삭제
    String email = jwtUtil.getEmail(accessToken);
    refreshTokenRepository.deleteByEmail(email);
  }

  private String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("해싱 알고리즘을 찾을 수 없습니다.", e);
    }
  }
}
