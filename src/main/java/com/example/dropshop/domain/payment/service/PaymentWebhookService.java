package com.example.dropshop.domain.payment.service;

import com.example.dropshop.common.config.PortOneProperties;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.lock.LockKeys;
import com.example.dropshop.common.lock.RedisLockService;
import com.example.dropshop.domain.auth.sse.service.SseEmitterService;
import com.example.dropshop.domain.dashboard.service.SellerDashboardRefreshService;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.enums.OrderStatus;
import com.example.dropshop.domain.order.facade.OrderFacadeService;
import com.example.dropshop.domain.payment.client.PortOneClient;
import com.example.dropshop.domain.payment.dto.request.PaymentWebhookRequest;
import com.example.dropshop.domain.payment.dto.response.PortOnePaymentResponse;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentStatus;
import com.example.dropshop.domain.payment.event.PaymentStatusChangedEvent;
import com.example.dropshop.domain.payment.exception.PaymentException;
import com.example.dropshop.domain.payment.outbox.PaymentOutboxPublisher;
import com.example.dropshop.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PortOne 웹훅 동기화를 처리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

  private final PaymentRepository paymentRepository;
  private final OrderFacadeService orderFacadeService;
  private final PortOneClient portOneClient;
  private final PortOneProperties portOneProperties;
  private final RedisLockService redisLockService;
  private final TransactionTemplate transactionTemplate;
  private final PaymentVerificationService paymentVerificationService;
  private final PaymentOutboxPublisher paymentOutboxPublisher;
  private final SellerDashboardRefreshService sellerDashboardRefreshService;
  private final SseEmitterService sseEmitterService;

  /**
   * PortOne 결제 식별자로 웹훅 동기화 처리.
   *
   * @param portOnePaymentId PortOne 결제 식별자
   * @return 상태가 반영된 결제 엔티티
   */
  public Payment handleWebhook(String portOnePaymentId) {
    if (portOnePaymentId == null || portOnePaymentId.isBlank()) {
      throw new PaymentException(ErrorCode.PAYMENT_WEBHOOK_PAYMENT_ID_REQUIRED);
    }

    Payment payment =
        paymentRepository
            .findByMerchantPaymentId(portOnePaymentId)
            .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_WEBHOOK_PAYMENT_NOT_FOUND));

    return redisLockService.executeWithLock(
        LockKeys.order(payment.getOrderId()),
        () -> transactionTemplate.execute(status -> handleWebhookInternal(portOnePaymentId)));
  }

  /**
   * 웹훅 요청 본문 검증 및 동기화 처리.
   *
   * @param request PortOne 웹훅 요청
   * @return 상태가 반영된 결제 엔티티
   */
  public Payment handleWebhook(PaymentWebhookRequest request) {
    if (!request.verifySignature(portOneProperties.resolvedWebhookSecret())) {
      throw new PaymentException(ErrorCode.PAYMENT_WEBHOOK_INVALID_SIGNATURE);
    }
    return handleWebhook(request.extractPortOnePaymentId());
  }

  private void applyWebhookResult(
      Payment payment, Order order, PortOnePaymentResponse portOnePayment) {
    if (payment.getStatus() != PaymentStatus.PENDING) {
      return;
    }

    paymentVerificationService.validatePortOneResponse(payment, portOnePayment);

    if (paymentVerificationService.isPaidStatus(portOnePayment.status())) {
      paymentVerificationService.validatePendingOrder(order);
      paymentVerificationService.validateOrderNotExpired(order);
      payment.complete(portOnePayment.transactionId());
      orderFacadeService.payOrderByPayment(order);
      refreshDashboardSafely(order, payment.getId());
      registerAfterCommit(
          () ->
              sseEmitterService.sendPaymentSuccessNotification(
                  order, "결제가 완료되었습니다. 주문번호: " + order.getOrderNumber()));
      publishPaymentStatusChanged(payment, order.getStatus(), "WEBHOOK", order.getUserId());
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
      publishPaymentStatusChanged(payment, order.getStatus(), "WEBHOOK", order.getUserId());
      return;
    }

    throw new PaymentException(ErrorCode.PAYMENT_PORTONE_NOT_PAID);
  }

  private Payment handleWebhookInternal(String portOnePaymentId) {
    Payment payment =
        paymentRepository
            .findByMerchantPaymentId(portOnePaymentId)
            .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_WEBHOOK_PAYMENT_NOT_FOUND));

    if (payment.getStatus() != PaymentStatus.PENDING) {
      return payment;
    }

    Order order = orderFacadeService.findOrderForPaymentWebhook(payment.getOrderId());
    PortOnePaymentResponse portOnePayment = portOneClient.getPayment(portOnePaymentId);
    applyWebhookResult(payment, order, portOnePayment);
    return payment;
  }

  private void publishPaymentStatusChanged(
      Payment payment, OrderStatus orderStatus, String source, Long buyerUserId) {
    paymentOutboxPublisher.save(
        new PaymentStatusChangedEvent(payment, orderStatus, source, buyerUserId));
  }

  private void refreshDashboardSafely(Order order, Long paymentId) {
    try {
      sellerDashboardRefreshService.refreshForOrder(order);
    } catch (RuntimeException e) {
      log.warn(
          "Seller dashboard refresh skipped after payment webhook. paymentId={}, orderId={}",
          paymentId,
          order.getId(),
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
