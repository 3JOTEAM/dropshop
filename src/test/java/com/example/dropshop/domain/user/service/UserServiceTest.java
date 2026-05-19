package com.example.dropshop.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.dropshop.common.exception.ServiceException;
import com.example.dropshop.domain.user.dto.request.PasswordUpdateRequest;
import com.example.dropshop.domain.user.dto.request.SignupRequest;
import com.example.dropshop.domain.user.entity.User;
import com.example.dropshop.domain.user.outbox.UserEventOutboxPublisher;
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
class UserServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private UserEventOutboxPublisher userEventOutboxPublisher;

  @InjectMocks private UserService userService;

  @Test
  @DisplayName("회원가입 성공 - 유효한 정보로 가입 시 유저가 저장된다")
  void signup_Success() {
    SignupRequest request = new SignupRequest("test@example.com", "Password123!", "tester");
    given(userRepository.existsByEmail(request.getEmail())).willReturn(false);
    given(passwordEncoder.encode(request.getPassword())).willReturn("encoded_password");

    userService.signup(request);

    verify(userRepository, times(1)).save(any(User.class));
  }

  @Test
  @DisplayName("회원가입 실패 - 중복된 이메일이 존재하면 예외가 발생한다")
  void signup_Fail_DuplicateEmail() {
    SignupRequest request = new SignupRequest("duplicate@example.com", "Password123!", "tester");
    given(userRepository.existsByEmail(request.getEmail())).willReturn(true);

    assertThatThrownBy(() -> userService.signup(request)).isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("비밀번호 변경 성공 - 기존 비밀번호 일치 시 변경된다")
  void updatePassword_Success() {
    String email = "test@example.com";
    PasswordUpdateRequest request = new PasswordUpdateRequest("oldPass123!", "newPass123!");
    User user = User.signup(email, "encoded_old_pass", "tester");

    given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
    given(passwordEncoder.matches(request.getOldPassword(), user.getPassword())).willReturn(true);
    given(passwordEncoder.encode(request.getNewPassword())).willReturn("encoded_new_pass");

    userService.updatePassword(email, request);

    assertThat(user.getPassword()).isEqualTo("encoded_new_pass");
  }

  @Test
  @DisplayName("비밀번호 변경 실패 - 기존 비밀번호 불일치 시 예외가 발생한다")
  void updatePassword_Fail_PasswordMismatch() {
    String email = "test@example.com";
    PasswordUpdateRequest request = new PasswordUpdateRequest("wrong_pass", "newPass123!");
    User user = User.signup(email, "encoded_old_pass", "tester");

    given(userRepository.findByEmail(email)).willReturn(Optional.of(user));
    given(passwordEncoder.matches(any(), any())).willReturn(false);

    assertThatThrownBy(() -> userService.updatePassword(email, request))
        .isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("회원 탈퇴 성공 - 유저 삭제가 호출된다")
  void withdraw_Success() {
    String email = "test@example.com";
    User user = User.signup(email, "pass", "tester");
    given(userRepository.findByEmail(email)).willReturn(Optional.of(user));

    userService.withdraw(email);

    verify(userRepository, times(1)).delete(user);
  }
}
