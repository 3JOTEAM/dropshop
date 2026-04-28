package com.example.dropshop.domain.payment.service;

import com.example.dropshop.common.lock.LockKeys;
import com.example.dropshop.common.lock.RedisLockService;
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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제 생성, 조회, 검증, 확정 로직을 처리하는 도메인 서비스다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final OrderFacadeService orderFacadeService;
  private final PortOneClient portOneClient;
  private final RedisLockService redisLockService;
  private final TransactionTemplate transactionTemplate;

  public record PaymentConfirmResult(Payment payment, OrderStatus orderStatus) {
  }

  /**
   * 주문에 대한 결제를 준비한다.
   *
   * @param orderId 주문 ID
   * @param amount 결제 금액
   * @param merchantPaymentId 중복 요청 방지 키이자 가맹점 결제 식별자
   * @param paymentMethod 결제 수단
   * @return 저장된 결제 엔티티
   * @throws OrderException 주문 상태가 결제 가능하지 않은 경우
   * @throws PaymentException 결제 금액 또는 중복 요청 정보가 유효하지 않은 경우
   */
  public Payment preparePayment(
      String email,
      Long orderId,
      BigDecimal amount,
      String merchantPaymentId,
      PaymentMethod paymentMethod
  ) {
    return redisLockService.executeWithLock(
        LockKeys.order(orderId),
        () -> transactionTemplate.execute(status -> preparePaymentInternal(
            email,
            orderId,
            amount,
            merchantPaymentId,
            paymentMethod
        ))
    );
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
  public Payment confirmPayment(Long paymentId, String email, String portOnePaymentId) {
    return confirmPaymentWithOrderStatus(paymentId, email, portOnePaymentId).payment();
  }

  /**
   * PortOne 결제 결과를 검증하고 주문 상태까지 함께 반환한다.
   */
  public PaymentConfirmResult confirmPaymentWithOrderStatus(
      Long paymentId,
      String email,
      String portOnePaymentId
  ) {
    Long orderId = findAccessiblePayment(paymentId, email).getOrderId();

    return redisLockService.executeWithLock(
        LockKeys.order(orderId),
        () -> transactionTemplate.execute(status -> confirmPaymentInternal(
            paymentId,
            email,
            portOnePaymentId
        ))
    );
  }

  private Payment preparePaymentInternal(
      String email,
      Long orderId,
      BigDecimal amount,
      String merchantPaymentId,
      PaymentMethod paymentMethod
  ) {
    Order order = orderFacadeService.findOrderForPayment(orderId, email);

    validateOrderPending(order);
    validateOrderNotExpired(order);
    validatePaymentAmount(order, amount);

    Optional<Payment> existingPayment = paymentRepository.findByMerchantPaymentId(merchantPaymentId);
    if (existingPayment.isPresent()) {
      return validateIdempotentPreparedPayment(existingPayment.get(), orderId, amount, paymentMethod);
    }

    validatePaymentNotExists(orderId);

    Payment payment = Payment.prepare(orderId, merchantPaymentId, paymentMethod, amount);
    return paymentRepository.save(payment);
  }

  private PaymentConfirmResult confirmPaymentInternal(
      Long paymentId,
      String email,
      String portOnePaymentId
  ) {
    Payment payment = findAccessiblePayment(paymentId, email);
    Order order = orderFacadeService.findOrderForPayment(payment.getOrderId(), email);

    validatePortOnePaymentId(payment, portOnePaymentId);

    if (payment.getStatus() != PaymentStatus.PENDING) {
      return new PaymentConfirmResult(payment, order.getStatus());
    }

    validateOrderPending(order);
    validateOrderNotExpired(order);

    PortOnePaymentResponse portOnePayment = portOneClient.getPayment(portOnePaymentId);
    applyConfirmationResult(payment, order, portOnePayment);
    return new PaymentConfirmResult(payment, order.getStatus());
  }

  private Payment findAccessiblePayment(Long paymentId, String email) {
    Payment payment = paymentRepository.findById(paymentId)
        .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
    orderFacadeService.findOrderForPayment(payment.getOrderId(), email);
    return payment;
  }

  private Payment validateIdempotentPreparedPayment(
      Payment existingPayment,
      Long orderId,
      BigDecimal amount,
      PaymentMethod paymentMethod
  ) {
    boolean matchesSameRequest = existingPayment.getOrderId().equals(orderId)
        && existingPayment.getAmount().compareTo(amount) == 0
        && existingPayment.getPaymentMethod() == paymentMethod;

    if (!matchesSameRequest) {
      throw new PaymentException(ErrorCode.PAYMENT_IDEMPOTENCY_KEY_CONFLICT);
    }

    return existingPayment;
  }

  private void applyConfirmationResult(
      Payment payment,
      Order order,
      PortOnePaymentResponse portOnePayment
  ) {
    validatePortOneResponse(payment, portOnePayment);

    if ("PAID".equals(portOnePayment.status())) {
      payment.complete(portOnePayment.transactionId());
      orderFacadeService.payOrderByPayment(order);
      return;
    }

    if (isFailureStatus(portOnePayment.status())) {
      payment.fail();
      if (order.getStatus() == OrderStatus.PENDING) {
        orderFacadeService.cancelOrderByPaymentFailure(order);
      }
      return;
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

  private void validatePortOnePaymentId(Payment payment, String portOnePaymentId) {
    if (!payment.getMerchantPaymentId().equals(portOnePaymentId)) {
      throw new PaymentException(ErrorCode.PAYMENT_PORTONE_MISMATCH);
    }
  }

  private void validatePortOneResponse(Payment payment, PortOnePaymentResponse portOnePayment) {
    if (portOnePayment == null) {
      throw new PaymentException(ErrorCode.PAYMENT_PORTONE_API_ERROR);
    }
    if (!payment.getMerchantPaymentId().equals(portOnePayment.id())) {
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
}
