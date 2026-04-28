package com.example.dropshop.domain.order.service;

import com.example.dropshop.common.lock.LockKeys;
import com.example.dropshop.common.lock.RedisLockService;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.entity.OrderItem;
import com.example.dropshop.domain.order.enums.OrderStatus;
import com.example.dropshop.domain.order.event.StockRestoreEvent;
import com.example.dropshop.domain.order.exception.OrderException;
import com.example.dropshop.domain.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문 서비스.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository orderRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final RedisLockService redisLockService;
  private final TransactionTemplate transactionTemplate;

  /**
   * 주문 생성.
   */
  @Transactional
  public Order createOrder(Long userId, Long dropId, Long productId,
      BigDecimal priceSnapshot, BigDecimal salePriceSnapshot,
      BigDecimal discountAmountSnapshot, String thumbnailUrlSnapshot) {

    if (orderRepository.existsByUserIdAndDropIdAndStatusIn(
        userId,
        dropId,
        List.of(OrderStatus.PENDING, OrderStatus.PAID))) {
      throw new OrderException(ErrorCode.ORDER_DUPLICATE);
    }

    Order order = Order.create(userId, dropId);
    OrderItem orderItem = OrderItem.create(order, productId,
        priceSnapshot, salePriceSnapshot, discountAmountSnapshot, thumbnailUrlSnapshot);
    order.addOrderItem(orderItem);

    return orderRepository.save(order);
  }

  /**
   * 단건 조회.
   */
  @Transactional(readOnly = true)
  public Order findOrderById(Long orderId, Long userId) {
    return orderRepository.findByIdAndUserId(orderId, userId)
        .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
  }

  /**
   * 내부 도메인 연동용 주문 단건 조회.
   */
  @Transactional(readOnly = true)
  public Order findOrderById(Long orderId) {
    return orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
  }

  /**
   * 목록 조회.
   */
  @Transactional(readOnly = true)
  public Page<Order> findAllOrdersByUserId(Long userId, Pageable pageable) {
    return orderRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
  }

  /**
   * 드랍의 주문 이력 존재 여부를 확인한다.
   */
  @Transactional(readOnly = true)
  public boolean existsOrderHistoryForDrop(Long dropId) {
    return orderRepository.existsByDropId(dropId);
  }

  /**
   * 수동 주문 취소.
   */
  public Order cancelOrder(Long orderId, Long userId) {
    return redisLockService.executeWithLock(
        LockKeys.order(orderId),
        () -> transactionTemplate.execute(status -> cancelOrderInternal(orderId, userId))
    );
  }

  /**
   * 주문 결제 완료 처리.
   */
  @Transactional
  public Order payOrder(Order order) {
    order.pay();
    return order;
  }

  /**
   * 주문 환불 완료 처리.
   *
   * @param order 주문 엔티티
   * @return 환불 완료 처리된 주문 엔티티
   */
  @Transactional
  public Order refundOrder(Order order) {
    order.refund();
    restoreDropStock(order);
    return order;
  }

  /**
   * 주문 취소 후 재고 복원 이벤트를 발행한다.
   */
  @Transactional
  public Order cancelOrderAndRestoreStock(Order order) {
    order.cancel();
    restoreDropStock(order);
    return order;
  }

  /**
   * 만료 주문 취소 처리.
   * 스케줄러가 30초마다 호출
   */
  public void cancelExpiredOrders() {
    List<Order> expiredOrders = orderRepository
        .findAllByStatusAndHoldExpiredAtBefore(OrderStatus.PENDING, LocalDateTime.now());
    expiredOrders.stream()
        .map(Order::getId)
        .forEach(this::cancelExpiredOrderSafely);
  }

  private void restoreDropStock(Order order) {
    int restoreQuantity = order.getOrderItems().stream()
        .mapToInt(OrderItem::getQuantity)
        .sum();
    eventPublisher.publishEvent(new StockRestoreEvent(order.getDropId(), restoreQuantity));
  }

  private Order cancelOrderInternal(Long orderId, Long userId) {
    Order order = orderRepository.findByIdAndUserId(orderId, userId)
        .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));
    return cancelOrderAndRestoreStock(order);
  }

  private void cancelExpiredOrderSafely(Long orderId) {
    redisLockService.tryExecuteWithLock(
        LockKeys.order(orderId),
        () -> transactionTemplate.execute(status -> {
          cancelExpiredOrderIfNeeded(orderId);
          return null;
        })
    );
  }

  private void cancelExpiredOrderIfNeeded(Long orderId) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new OrderException(ErrorCode.ORDER_NOT_FOUND));

    if (order.getStatus() != OrderStatus.PENDING || !order.isHoldExpired()) {
      return;
    }

    cancelOrderAndRestoreStock(order);
  }

}
