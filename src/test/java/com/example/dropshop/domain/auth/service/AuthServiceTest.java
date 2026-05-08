package com.example.dropshop.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.example.dropshop.common.jwt.JwtUtil;
import com.example.dropshop.common.kafka.producer.EventKafkaProducer;
import com.example.dropshop.domain.auth.dto.request.LoginRequest;
import com.example.dropshop.domain.auth.dto.response.TokenResponse;
import com.example.dropshop.domain.auth.repository.RefreshTokenRepository;
import com.example.dropshop.domain.user.entity.User;
import com.example.dropshop.domain.user.enums.UserRole;
import com.example.dropshop.domain.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @InjectMocks private AuthService authService;

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private JwtUtil jwtUtil;

  @Mock private RefreshTokenRepository refreshTokenRepository;

  @Mock private TokenBlacklistService tokenBlacklistService;

  @Mock private EventKafkaProducer eventKafkaProducer;

  @Mock private SseEmitterService sseEmitterService;

  @Test
  @DisplayName("로그인 성공 테스트")
  void login_Success() {
    // 1. Given
    LoginRequest request = new LoginRequest("test@test.com", "password123!");

    User user = mock(User.class);
    given(user.getEmail()).willReturn("test@test.com");
    given(user.getPassword()).willReturn("encodedPassword");
    given(user.getRole()).willReturn(UserRole.USER);

    given(userRepository.findByEmail(anyString())).willReturn(Optional.of(user));
    given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

    given(jwtUtil.createAccessToken(anyString(), anyString())).willReturn("mock-access-token");
    given(jwtUtil.createRefreshToken(anyString())).willReturn("mock-refresh-token");

    // 2. When
    TokenResponse response = authService.login(request);

    // 3. Then
    assertThat(response).isNotNull();
    assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
    assertThat(response.getRefreshToken()).isEqualTo("mock-refresh-token");
  }
}
