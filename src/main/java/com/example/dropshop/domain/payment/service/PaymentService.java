package com.example.dropshop.domain.payment.service;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.lock.LockKeys;
import com.example.dropshop.common.lock.RedisLockService;
import com.example.dropshop.domain.auth.sse.service.SseEmitterService;
import com.example.dropshop.domain.dashboard.service.SellerDashboardRefreshService;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.enums.OrderStatus;
import com.example.dropshop.domain.order.exception.OrderException;
import com.example.dropshop.domain.order.facade.OrderFacadeService;
import com.example.dropshop.domain.payment.client.PortOneClient;
import com.example.dropshop.domain.payment.dto.response.PortOnePaymentResponse;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentMethod;
import com.example.dropshop.domain.payment.enums.PaymentStatus;
import com.example.dropshop.domain.payment.event.PaymentStatusChangedEvent;
import com.example.dropshop.domain.payment.exception.PaymentException;
import com.example.dropshop.domain.payment.outbox.PaymentOutboxPublisher;
import com.example.dropshop.domain.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 결제 생성, 검증, 확정 로직을 처리하는 도메인 서비스. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final OrderFacadeService orderFacadeService;
  private final PortOneClient portOneClient;
  private final RedisLockService redisLockService;
  private final TransactionTemplate transactionTemplate;
  private final PaymentVerificationService paymentVerificationService;
  private final PaymentOutboxPublisher paymentOutboxPublisher;
  private final SellerDashboardRefreshService sellerDashboardRefreshService;
  private final SseEmitterService sseEmitterService;

  /**
   * 결제 확정 결과.
   *
   * @param payment 결제 엔티티
   * @param orderStatus 주문 상태
   */
  public record PaymentConfirmResult(Payment payment, OrderStatus orderStatus) {}

  /**
   * 주문에 대한 결제를 준비한다.
   *
   * @param email 인증된 사용자 이메일
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
      PaymentMethod paymentMethod) {
    return redisLockService.executeWithLock(
        LockKeys.order(orderId),
        () ->
            transactionTemplate.execute(
                status ->
                    preparePaymentInternal(
                        email, orderId, amount, merchantPaymentId, paymentMethod)));
  }

  /**
   * PortOne 결제 결과를 검증하고 내부 결제를 확정한다.
   *
   * @param paymentId 결제 ID
   * @param email 인증된 사용자 이메일
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
   *
   * @param paymentId 결제 ID
   * @param email 인증된 사용자 이메일
   * @param portOnePaymentId PortOne 결제 식별자
   * @return 결제와 주문 상태
   */
  public PaymentConfirmResult confirmPaymentWithOrderStatus(
      Long paymentId, String email, String portOnePaymentId) {
    Long orderId = findAccessiblePayment(paymentId, email).getOrderId();

    return redisLockService.executeWithLock(
        LockKeys.order(orderId),
        () ->
            transactionTemplate.execute(
                status -> confirmPaymentInternal(paymentId, email, portOnePaymentId)));
  }

  private Payment preparePaymentInternal(
      String email,
      Long orderId,
      BigDecimal amount,
      String merchantPaymentId,
      PaymentMethod paymentMethod) {
    Order order = orderFacadeService.findOrderForPayment(orderId, email);

    paymentVerificationService.validatePendingOrder(order);
    paymentVerificationService.validateOrderNotExpired(order);
    validatePaymentAmount(order, amount);

    Optional<Payment> existingPayment =
        paymentRepository.findByMerchantPaymentId(merchantPaymentId);
    if (existingPayment.isPresent()) {
      return validateIdempotentPreparedPayment(
          existingPayment.get(), orderId, amount, paymentMethod);
    }

    // PENDING 상태의 기존 결제가 있으면 merchantPaymentId만 갱신해서 재사용
    Optional<Payment> pendingPayment = paymentRepository.findByOrderId(orderId);
    if (pendingPayment.isPresent()
        && pendingPayment.get().getStatus() == PaymentStatus.PENDING) {
      Payment existing = pendingPayment.get();
      existing.renewMerchantPaymentId(merchantPaymentId);
      return paymentRepository.save(existing);
    }

    validatePaymentNotExists(orderId);

    Payment payment = Payment.prepare(orderId, merchantPaymentId, paymentMethod, amount);
    return paymentRepository.save(payment);
  }

  private PaymentConfirmResult confirmPaymentInternal(
      Long paymentId, String email, String portOnePaymentId) {
    Payment payment = findAccessiblePayment(paymentId, email);
    Order order = orderFacadeService.findOrderForPayment(payment.getOrderId(), email);

    paymentVerificationService.validatePortOnePaymentId(payment, portOnePaymentId);

    if (payment.getStatus() != PaymentStatus.PENDING) {
      return new PaymentConfirmResult(payment, order.getStatus());
    }

    paymentVerificationService.validatePendingOrder(order);
    paymentVerificationService.validateOrderNotExpired(order);

    PortOnePaymentResponse portOnePayment = portOneClient.getPayment(portOnePaymentId);
    applyConfirmationResult(payment, order, portOnePayment);
    return new PaymentConfirmResult(payment, order.getStatus());
  }

  private Payment findAccessiblePayment(Long paymentId, String email) {
    Payment payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
    orderFacadeService.findOrderForPayment(payment.getOrderId(), email);
    return payment;
  }

  private Payment validateIdempotentPreparedPayment(
      Payment existingPayment, Long orderId, BigDecimal amount, PaymentMethod paymentMethod) {
    boolean matchesSameRequest =
        existingPayment.getOrderId().equals(orderId)
            && existingPayment.getAmount().compareTo(amount) == 0
            && existingPayment.getPaymentMethod() == paymentMethod;

    if (!matchesSameRequest) {
      throw new PaymentException(ErrorCode.PAYMENT_IDEMPOTENCY_KEY_CONFLICT);
    }

    return existingPayment;
  }

  private void applyConfirmationResult(
      Payment payment, Order order, PortOnePaymentResponse portOnePayment) {
    paymentVerificationService.validatePortOneResponse(payment, portOnePayment);

    if (paymentVerificationService.isPaidStatus(portOnePayment.status())) {
      payment.complete(portOnePayment.transactionId());
      orderFacadeService.payOrderByPayment(order);
      refreshDashboardSafely(order, payment.getId(), "CONFIRM_API");
      registerAfterCommit(
          () ->
              sseEmitterService.sendPaymentSuccessNotification(
                  order, "결제가 완료되었습니다. 주문번호: " + order.getOrderNumber()));
      publishPaymentStatusChanged(payment, order.getStatus(), "CONFIRM_API", order.getUserId());
      return;
    }

    if (paymentVerificationService.isFailureStatus(portOnePayment.status())) {
      payment.fail();
      if (order.getStatus() == OrderStatus.PENDING) {
        orderFacadeService.cancelOrderByPaymentFailure(order);
      }
      registerAfterCommit(
          () ->
              sseEmitterService.sendPaymentFailNotification(
                  order, "결제에 실패했습니다. 주문번호: " + order.getOrderNumber()));
      publishPaymentStatusChanged(payment, order.getStatus(), "CONFIRM_API", order.getUserId());
      return;
    }

    throw new PaymentException(ErrorCode.PAYMENT_PORTONE_NOT_PAID);
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

  private void publishPaymentStatusChanged(
      Payment payment, OrderStatus orderStatus, String source, Long buyerUserId) {
    paymentOutboxPublisher.save(
        new PaymentStatusChangedEvent(payment, orderStatus, source, buyerUserId));
  }

  private void refreshDashboardSafely(Order order, Long paymentId, String source) {
    try {
      sellerDashboardRefreshService.refreshForOrder(order);
    } catch (RuntimeException e) {
      log.warn(
          "Seller dashboard refresh skipped after payment update. paymentId={}, orderId={}, source={}",
          paymentId,
          order.getId(),
          source,
          e);
    }
  }

  private void registerAfterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
      return;
    }

    action.run();
  }
}
