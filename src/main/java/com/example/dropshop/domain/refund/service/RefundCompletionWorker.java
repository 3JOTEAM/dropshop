package com.example.dropshop.domain.refund.service;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.enums.OrderStatus;
import com.example.dropshop.domain.order.facade.OrderFacadeService;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentStatus;
import com.example.dropshop.domain.payment.exception.PaymentException;
import com.example.dropshop.domain.payment.repository.PaymentRepository;
import com.example.dropshop.domain.refund.entity.Refund;
import com.example.dropshop.domain.refund.entity.RefundStatus;
import com.example.dropshop.domain.refund.exception.RefundException;
import com.example.dropshop.domain.refund.repository.RefundRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불 완료 흐름의 트랜잭션 경계를 분리해 처리한다.
 */
@Service
@RequiredArgsConstructor
public class RefundCompletionWorker {

  private final RefundRepository refundRepository;
  private final PaymentRepository paymentRepository;
  private final OrderFacadeService orderFacadeService;

  /**
   * 외부 PG 환불 호출 전 내부 환불을 처리 중 상태로 전이한다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public RefundCompletionCommand prepareRefundCompletion(Long refundId, String email) {
    Refund refund = findRefund(refundId);
    validateRefundStatus(refund, RefundStatus.APPROVED);

    Payment payment = getPayment(refund.getPaymentId());
    validateRefundablePayment(payment);
    validatePortOneTransactionId(payment);

    Order order = orderFacadeService.findOrderForPayment(payment.getOrderId(), email);
    validateRefundableOrder(order);

    refund.startProcessing();

    return new RefundCompletionCommand(
        refund.getId(),
        order.getId(),
        payment.getPortOneTransactionId(),
        refund.getRefundAmount(),
        refund.getRefundReason() == null ? "환불 요청" : refund.getRefundReason()
    );
  }

  /**
   * 외부 PG 환불 실패 시 내부 환불 상태를 승인으로 되돌린다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void revertRefundCompletion(Long refundId) {
    Refund refund = findRefund(refundId);
    if (refund.getStatus() == RefundStatus.PROCESSING) {
      refund.revertToApproved();
    }
  }

  /**
   * 외부 PG 환불 성공 후 내부 환불과 주문 상태를 마감한다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Refund finalizeRefundCompletion(Long refundId, Long orderId) {
    Refund refund = findRefund(refundId);
    if (refund.getStatus() == RefundStatus.COMPLETED) {
      return refund;
    }
    validateRefundStatus(refund, RefundStatus.PROCESSING);

    Order order = orderFacadeService.findOrderForPaymentWebhook(orderId);
    if (order.getStatus() == OrderStatus.PAID) {
      orderFacadeService.refundOrderByRefund(order);
    } else if (order.getStatus() != OrderStatus.REFUNDED) {
      throw new RefundException(ErrorCode.REFUND_ORDER_INVALID_STATUS);
    }

    refund.complete();
    return refund;
  }

  private Payment getPayment(Long paymentId) {
    return paymentRepository.findById(paymentId)
        .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
  }

  private Refund findRefund(Long refundId) {
    return refundRepository.findById(refundId)
        .orElseThrow(() -> new RefundException(ErrorCode.REFUND_NOT_FOUND));
  }

  private void validateRefundablePayment(Payment payment) {
    if (payment.getStatus() != PaymentStatus.COMPLETED) {
      throw new RefundException(ErrorCode.REFUND_PAYMENT_NOT_COMPLETED);
    }
  }

  private void validatePortOneTransactionId(Payment payment) {
    if (payment.getPortOneTransactionId() == null || payment.getPortOneTransactionId().isBlank()) {
      throw new PaymentException(ErrorCode.PAYMENT_TRANSACTION_ID_REQUIRED);
    }
  }

  private void validateRefundableOrder(Order order) {
    if (order.getStatus() != OrderStatus.PAID) {
      throw new RefundException(ErrorCode.REFUND_ORDER_INVALID_STATUS);
    }
  }

  private void validateRefundStatus(Refund refund, RefundStatus expectedStatus) {
    if (refund.getStatus() != expectedStatus) {
      throw new RefundException(ErrorCode.REFUND_INVALID_STATUS);
    }
  }

  public record RefundCompletionCommand(
      Long refundId,
      Long orderId,
      String portOneTransactionId,
      BigDecimal refundAmount,
      String refundReason
  ) {
  }
}
