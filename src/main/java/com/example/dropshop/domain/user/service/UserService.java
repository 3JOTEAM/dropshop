package com.example.dropshop.domain.user.service;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.exception.ServiceException;
import com.example.dropshop.domain.user.dto.request.PasswordUpdateRequest;
import com.example.dropshop.domain.user.dto.request.SignupRequest;
import com.example.dropshop.domain.user.entity.User;
import com.example.dropshop.domain.user.event.UserSignupEvent;
import com.example.dropshop.domain.user.outbox.UserEventOutboxPublisher;
import com.example.dropshop.domain.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserEventOutboxPublisher userEventOutboxPublisher;

  @Transactional
  public void signup(SignupRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new ServiceException(ErrorCode.DUPLICATE_EMAIL);
    }

    String encodedPassword = passwordEncoder.encode(request.getPassword());
    User user = User.signup(request.getEmail(), encodedPassword, request.getNickname());
    userRepository.save(user);

    // 회원가입 이벤트를 동일 트랜잭션 내 아웃박스 테이블에 저장한다.
    // 스케줄러(UserEventOutboxPublisher)가 5초마다 Kafka로 발행하므로
    // DB 커밋 이후 Kafka 장애가 발생해도 이벤트가 유실되지 않는다.
    userEventOutboxPublisher.save(UserSignupEvent.of(user.getEmail()), hashEmail(user.getEmail()));
  }

  @Transactional
  public void updatePassword(String email, PasswordUpdateRequest request) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND));

    if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
      throw new ServiceException(ErrorCode.PASSWORD_MISMATCH);
    }

    user.changePassword(passwordEncoder.encode(request.getNewPassword()));
  }

  @Transactional
  public void withdraw(String email) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND));

    user.withdraw();
    userRepository.delete(user);
  }

  private static String hashEmail(String email) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(email.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }
}
