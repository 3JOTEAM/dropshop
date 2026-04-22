package com.example.dropshop.domain.user.service;

import com.example.dropshop.domain.user.entity.User;
import com.example.dropshop.domain.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 도메인 파사드 서비스.
 */
@Service
@RequiredArgsConstructor
public class UserFacadeService {

  private final UserRepository userRepository;

  /**
   * 사용자 ID로 사용자 정보를 조회한다.
   */
  @Transactional(readOnly = true)
  public Optional<User> findById(Long userId) {
    return userRepository.findById(userId);
  }

  /**
   * 이메일로 사용자 정보를 조회한다.
   */
  @Transactional(readOnly = true)
  public Optional<User> findByEmail(String email) {
    return userRepository.findByEmail(email);
  }
}

