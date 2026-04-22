package com.example.dropshop.domain.seller.service;

import com.example.dropshop.domain.seller.entity.Seller;
import com.example.dropshop.domain.seller.repository.SellerRepository;
import com.example.dropshop.domain.user.entity.User;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 도메인 파사드 서비스.
 */
@Service
@RequiredArgsConstructor
public class SellerFacadeService {

  private final SellerRepository sellerRepository;

  /**
   * 사용자 정보로 판매자 정보를 조회한다.
   */
  @Transactional(readOnly = true)
  public Optional<Seller> findByUser(User user) {
    return sellerRepository.findByUser(user);
  }
}

