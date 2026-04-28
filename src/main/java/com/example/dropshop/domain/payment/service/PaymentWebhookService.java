package com.example.dropshop.domain.payment.service;

import com.example.dropshop.common.config.PortOneProperties;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.lock.LockKeys;
import com.example.dropshop.common.lock.RedisLockService;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.enums.OrderStatus;
import com.example.dropshop.domain.order.exception.OrderException;
import com.example.dropshop.domain.order.facade.OrderFacadeService;
import com.example.dropshop.domain.payment.client.PortOneClient;
import com.example.dropshop.domain.payment.dto.request.PaymentWebhookRequest;
import com.example.dropshop.domain.payment.dto.response.PortOnePaymentResponse;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentStatus;
import com.example.dropshop.domain.payment.exception.PaymentException;
import com.example.dropshop.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PortOne 웹훅 동기화를 처리한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

  private final PaymentRepository paymentRepository;
  private final OrderFacadeService orderFacadeService;
  private final PortOneClient portOneClient;
  private final PortOneProperties portOneProperties;
  private final RedisLockService redisLockService;
  private final TransactionTemplate transactionTemplate;

  public Payment handleWebhook(String portOnePaymentId) {
    if (portOnePaymentId == null || portOnePaymentId.isBlank()) {
      throw new PaymentException(ErrorCode.PAYMENT_WEBHOOK_PAYMENT_ID_REQUIRED);
    }

    Payment payment = paymentRepository.findByMerchantPaymentId(portOnePaymentId)
        .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_WEBHOOK_PAYMENT_NOT_FOUND));

    return redisLockService.executeWithLock(
        LockKeys.order(payment.getOrderId()),
        () -> transactionTemplate.execute(status -> handleWebhookInternal(portOnePaymentId))
    );
  }

  public Payment handleWebhook(PaymentWebhookRequest request) {
    if (!request.verifySignature(portOneProperties.resolvedWebhookSecret())) {
      throw new PaymentException(ErrorCode.PAYMENT_WEBHOOK_INVALID_SIGNATURE);
    }
    return handleWebhook(request.extractPortOnePaymentId());
  }

  private void applyWebhookResult(
      Payment payment,
      Order order,
      PortOnePaymentResponse portOnePayment
  ) {
    if (payment.getStatus() != PaymentStatus.PENDING) {
      return;
    }

    validatePortOneResponse(payment, portOnePayment);

    if ("PAID".equals(portOnePayment.status())) {
      validateOrderPending(order);
      validateOrderNotExpired(order);
      payment.complete(portOnePayment.transactionId());
      orderFacadeService.payOrderByPayment(order);
      return;
    }

    if (isFailureStatus(portOnePayment.status())) {
      payment.fail();
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

  private Payment handleWebhookInternal(String portOnePaymentId) {
    Payment payment = paymentRepository.findByMerchantPaymentId(portOnePaymentId)
        .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_WEBHOOK_PAYMENT_NOT_FOUND));

    if (payment.getStatus() != PaymentStatus.PENDING) {
      return payment;
    }

    Order order = orderFacadeService.findOrderForPaymentWebhook(payment.getOrderId());
    PortOnePaymentResponse portOnePayment = portOneClient.getPayment(portOnePaymentId);
    applyWebhookResult(payment, order, portOnePayment);
    return payment;
  }
}
