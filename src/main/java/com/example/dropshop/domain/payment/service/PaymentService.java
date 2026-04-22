package com.example.dropshop.domain.payment.service;

import com.example.dropshop.common.config.PortOneProperties;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.enums.OrderStatus;
import com.example.dropshop.domain.order.exception.OrderException;
import com.example.dropshop.domain.order.facade.OrderFacadeService;
import com.example.dropshop.domain.payment.client.PortOneClient;
import com.example.dropshop.domain.payment.dto.response.PortOnePaymentResponse;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentMethod;
import com.example.dropshop.domain.payment.enums.PaymentStatus;
import com.example.dropshop.domain.payment.exception.PaymentException;
import com.example.dropshop.domain.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 생성, 조회, 검증, 확정 로직을 처리하는 도메인 서비스다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final OrderFacadeService orderFacadeService;
  private final PortOneClient portOneClient;
  private final PortOneProperties portOneProperties;

  /**
   * 주문에 대한 결제를 준비한다.
   *
   * @param orderId 주문 ID
   * @param amount 결제 금액
   * @param idempotencyKey 중복 요청 방지 키
   * @param paymentMethod 결제 수단
   * @return 저장된 결제 엔티티
   * @throws OrderException 주문 상태가 결제 가능하지 않은 경우
   * @throws PaymentException 결제 금액 또는 중복 요청 정보가 유효하지 않은 경우
   */
  @Transactional
  public Payment preparePayment(
      String email,
      Long orderId,
      BigDecimal amount,
      String idempotencyKey,
      PaymentMethod paymentMethod
  ) {
    Order order = getOrder(orderId, email);

    validateOrderPending(order);
    validateOrderNotExpired(order);
    validatePaymentAmount(order, amount);
    validatePaymentNotExists(orderId);
    validateIdempotencyKeyNotExists(idempotencyKey);

    Payment payment = Payment.prepare(orderId, idempotencyKey, paymentMethod, amount);
    return paymentRepository.save(payment);
  }

  /**
   * 결제를 단건 조회한다.
   *
   * @param paymentId 결제 ID
   * @return 조회된 결제 엔티티
   * @throws PaymentException 결제를 찾을 수 없는 경우
   */
  @Transactional(readOnly = true)
  public Payment getPayment(Long paymentId, String email) {
    Payment payment = paymentRepository.findById(paymentId)
        .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
    validateOrderOwnership(payment.getOrderId(), email);
    return payment;
  }

  /**
   * 주문을 단건 조회한다.
   *
   * @param orderId 주문 ID
   * @return 조회된 주문 엔티티
   * @throws OrderException 주문을 찾을 수 없는 경우
   */
  @Transactional(readOnly = true)
  public Order getOrder(Long orderId, String email) {
    return orderFacadeService.findOrderForPayment(orderId, email);
  }

  /**
   * PortOne 결제 결과를 검증하고 내부 결제를 확정한다.
   *
   * @param paymentId 결제 ID
   * @param portOnePaymentId PortOne 결제 식별자
   * @return 상태가 반영된 결제 엔티티
   * @throws PaymentException PortOne 응답 검증 또는 결제 확정에 실패한 경우
   * @throws OrderException 주문 상태가 결제 가능하지 않은 경우
   */
  @Transactional
  public Payment confirmPayment(Long paymentId, String email, String portOnePaymentId) {
    Payment payment = getPayment(paymentId, email);
    Order order = getOrder(payment.getOrderId(), email);

    validatePaymentPending(payment);
    validateOrderPending(order);
    validateOrderNotExpired(order);
    validatePortOnePaymentId(payment, portOnePaymentId);

    PortOnePaymentResponse portOnePayment = portOneClient.getPayment(portOnePaymentId);
    return applyPortOnePaymentResult(payment, order, portOnePayment, true);
  }

  /**
   * PortOne 웹훅을 수신해 내부 결제 상태를 동기화한다.
   */
  @Transactional
  public Payment handleWebhook(String portOnePaymentId) {
    if (portOnePaymentId == null || portOnePaymentId.isBlank()) {
      throw new PaymentException(ErrorCode.PAYMENT_WEBHOOK_PAYMENT_ID_REQUIRED);
    }

    Payment payment = paymentRepository.findByIdempotencyKey(portOnePaymentId)
        .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_WEBHOOK_PAYMENT_NOT_FOUND));
    Order order = orderFacadeService.findOrderForPaymentWebhook(payment.getOrderId());

    PortOnePaymentResponse portOnePayment = portOneClient.getPayment(portOnePaymentId);
    return applyPortOnePaymentResult(payment, order, portOnePayment, false);
  }

  /**
   * PortOne 상점 식별자를 반환한다.
   *
   * @return PortOne storeId
   */
  public String getStoreId() {
    return portOneProperties.storeId();
  }

  /**
   * PortOne 채널 키를 반환한다.
   *
   * @return PortOne channelKey
   */
  public String getChannelKey() {
    return portOneProperties.channelKey();
  }

  /**
   * PortOne 결제 후 리다이렉트 URL을 반환한다.
   *
   * @return PortOne redirectUrl
   */
  public String getRedirectUrl() {
    return portOneProperties.redirectUrl();
  }

  private Payment applyPortOnePaymentResult(
      Payment payment,
      Order order,
      PortOnePaymentResponse portOnePayment,
      boolean cancelOrderOnFailure
  ) {
    validatePortOneResponse(payment, portOnePayment);

    if (payment.getStatus() != PaymentStatus.PENDING) {
      return payment;
    }

    if ("PAID".equals(portOnePayment.status())) {
      validateOrderPending(order);
      validateOrderNotExpired(order);
      payment.complete(portOnePayment.transactionId());
      orderFacadeService.payOrderByPayment(order);
      return payment;
    }

    if (isFailureStatus(portOnePayment.status())) {
      if (cancelOrderOnFailure) {
        payment.fail();
        if (order.getStatus() == OrderStatus.PENDING) {
          orderFacadeService.cancelOrderByPaymentFailure(order);
        }
      }
      return payment;
    }

    throw new PaymentException(ErrorCode.PAYMENT_PORTONE_NOT_PAID);
  }

  private void validateOrderPending(Order order) {
    if (order.getStatus() != OrderStatus.PENDING) {
      throw new OrderException(ErrorCode.ORDER_INVALID_STATUS);
    }
  }

  private void validateOrderNotExpired(Order order) {
    if (order.isHoldExpired()) {
      throw new OrderException(ErrorCode.ORDER_HOLD_EXPIRED);
    }
  }

  private void validatePaymentAmount(Order order, BigDecimal amount) {
    if (order.getTotalAmount().compareTo(amount) != 0) {
      throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }
  }

  private void validatePaymentNotExists(Long orderId) {
    if (paymentRepository.existsByOrderId(orderId)) {
      throw new PaymentException(ErrorCode.PAYMENT_ALREADY_EXISTS);
    }
  }

  private void validateIdempotencyKeyNotExists(String idempotencyKey) {
    if (paymentRepository.existsByIdempotencyKey(idempotencyKey)) {
      throw new PaymentException(ErrorCode.PAYMENT_IDEMPOTENCY_KEY_CONFLICT);
    }
  }

  private void validatePaymentPending(Payment payment) {
    if (payment.getStatus() != PaymentStatus.PENDING) {
      throw new PaymentException(ErrorCode.PAYMENT_CONFIRM_FORBIDDEN);
    }
  }

  private void validatePortOnePaymentId(Payment payment, String portOnePaymentId) {
    if (!payment.getIdempotencyKey().equals(portOnePaymentId)) {
      throw new PaymentException(ErrorCode.PAYMENT_PORTONE_MISMATCH);
    }
  }

  private void validatePortOneResponse(Payment payment, PortOnePaymentResponse portOnePayment) {
    if (portOnePayment == null) {
      throw new PaymentException(ErrorCode.PAYMENT_PORTONE_API_ERROR);
    }
    if (!payment.getIdempotencyKey().equals(portOnePayment.id())) {
      throw new PaymentException(ErrorCode.PAYMENT_PORTONE_MISMATCH);
    }
    if (portOnePayment.amount() == null || portOnePayment.amount().total() == null) {
      throw new PaymentException(ErrorCode.PAYMENT_PORTONE_API_ERROR);
    }
    if (payment.getAmount().compareTo(portOnePayment.amount().total()) != 0) {
      throw new PaymentException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
    }
    if (portOnePayment.status() == null || portOnePayment.status().isBlank()) {
      throw new PaymentException(ErrorCode.PAYMENT_PORTONE_API_ERROR);
    }
  }

  private boolean isFailureStatus(String status) {
    return "FAILED".equals(status) || "CANCELLED".equals(status);
  }

  private void validateOrderOwnership(Long orderId, String email) {
    orderFacadeService.findOrderForPayment(orderId, email);
  }
}
