package com.example.dropshop.domain.order.service;

import com.example.dropshop.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인에서 주문 이력 존재 여부만 조회할 때 사용하는 서비스다.
 */
@Service
@RequiredArgsConstructor
public class OrderHistoryQueryService {

  private final OrderRepository orderRepository;

  @Transactional(readOnly = true)
  public boolean existsOrderHistoryForDrop(Long dropId) {
    return orderRepository.existsByDropId(dropId);
  }
}
