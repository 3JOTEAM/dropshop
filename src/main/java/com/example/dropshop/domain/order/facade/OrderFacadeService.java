package com.example.dropshop.domain.order.facade;

import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.service.DropsFacadeService;
import com.example.dropshop.domain.order.dto.request.OrderCreateRequest;
import com.example.dropshop.domain.order.dto.response.OrderCreateResponse;
import com.example.dropshop.domain.order.dto.response.OrderDetailResponse;
import com.example.dropshop.domain.order.dto.response.OrderListItemResponse;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.service.OrderService;
import com.example.dropshop.domain.user.service.UserFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 주문 파사드 서비스. */
@Service
@RequiredArgsConstructor
public class OrderFacadeService {

  private final OrderService orderService;
  private final DropsFacadeService dropsFacadeService;
  private final UserFacadeService userFacadeService;

  /**
   * 주문 생성.
   *
   * @param email 인증된 사용자 이메일
   * @param request 주문 생성 요청
   * @return 생성된 주문 응답
   */
  @Transactional
  public OrderCreateResponse createOrder(String email, OrderCreateRequest request) {
    Long userId = getUserIdByEmail(email);
    Drops drops =
        dropsFacadeService.reserveStockForOrder(request.getDropId(), request.getProductId(), 1);

    Order order =
        orderService.createOrder(
            userId,
            request.getDropId(),
            request.getProductId(),
            drops.getProduct().getPrice(),
            drops.getProduct().getSalePrice(),
            drops.getProduct().getDiscountAmount(),
            drops.getProduct().getThumbnailUrl());

    return OrderCreateResponse.from(order);
  }

  /**
   * 주문 단건 조회.
   *
   * @param orderId 주문 ID
   * @param email 인증된 사용자 이메일
   * @return 주문 상세 응답
   */
  public OrderDetailResponse findOrderById(Long orderId, String email) {
    return OrderDetailResponse.from(orderService.findOrderById(orderId, getUserIdByEmail(email)));
  }

  /**
   * 결제 도메인에서 사용할 주문을 조회한다.
   *
   * @param orderId 주문 ID
   * @param email 인증된 사용자 이메일
   * @return 주문 엔티티
   */
  @Transactional(readOnly = true)
  public Order findOrderForPayment(Long orderId, String email) {
    return orderService.findOrderById(orderId, getUserIdByEmail(email));
  }

  /**
   * 웹훅 등 내부 연동에서 사용할 주문을 조회한다.
   *
   * @param orderId 주문 ID
   * @return 주문 엔티티
   */
  @Transactional(readOnly = true)
  public Order findOrderForPaymentWebhook(Long orderId) {
    return orderService.findOrderById(orderId);
  }

  /**
   * 결제 성공에 따른 주문 완료 처리.
   *
   * @param order 주문 엔티티
   * @return 결제 완료된 주문 엔티티
   */
  public Order payOrderByPayment(Order order) {
    return orderService.payOrder(order);
  }

  /**
   * 결제 실패에 따른 주문 취소 및 재고 복원 처리.
   *
   * @param order 주문 엔티티
   * @return 취소된 주문 엔티티
   */
  public Order cancelOrderByPaymentFailure(Order order) {
    return orderService.cancelOrderAndRestoreStock(order);
  }

  /**
   * 환불 완료에 따른 주문 환불 처리.
   *
   * @param order 주문 엔티티
   * @return 환불 완료 처리된 주문 엔티티
   */
  public Order refundOrderByRefund(Order order) {
    return orderService.refundOrder(order);
  }

  /**
   * 주문 목록 조회.
   *
   * @param email 인증된 사용자 이메일
   * @param pageable 페이지 정보
   * @return 주문 목록 응답
   */
  @Transactional(readOnly = true)
  public Page<OrderListItemResponse> findOrdersByUserId(String email, Pageable pageable) {
    return orderService
        .findAllOrdersByUserId(getUserIdByEmail(email), pageable)
        .map(OrderListItemResponse::from);
  }

  /**
   * 드랍의 주문 이력 존재 여부를 확인한다.
   *
   * @param dropId 드랍 ID
   * @return 주문 이력 존재 여부
   */
  @Transactional(readOnly = true)
  public boolean existsOrderHistoryForDrop(Long dropId) {
    return orderService.existsOrderHistoryForDrop(dropId);
  }

  /**
   * 주문 수동 취소.
   *
   * @param orderId 주문 ID
   * @param email 인증된 사용자 이메일
   * @return 취소된 주문 응답
   */
  public OrderDetailResponse cancelOrder(Long orderId, String email) {
    return OrderDetailResponse.from(orderService.cancelOrder(orderId, getUserIdByEmail(email)));
  }

  private Long getUserIdByEmail(String email) {
    return userFacadeService.getUserIdByEmail(email);
  }
}
