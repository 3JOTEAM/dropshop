package com.example.dropshop.domain.refund.service;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.lock.LockKeys;
import com.example.dropshop.common.lock.RedisLockService;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.payment.client.PortOneClient;
import com.example.dropshop.domain.payment.dto.response.PortOnePaymentResponse;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.exception.PaymentException;
import com.example.dropshop.domain.payment.repository.PaymentRepository;
import com.example.dropshop.domain.refund.entity.Refund;
import com.example.dropshop.domain.refund.enums.RefundStatus;
import com.example.dropshop.domain.refund.exception.RefundException;
import com.example.dropshop.domain.refund.repository.RefundRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** PROCESSING 상태로 고착된 환불을 PortOne 상태 기준으로 복구한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundRecoveryService {

  private static final String PORTONE_STATUS_PAID = "PAID";
  private static final String PORTONE_STATUS_CANCELLED = "CANCELLED";

  private final RefundRepository refundRepository;
  private final PaymentRepository paymentRepository;
  private final PortOneClient portOneClient;
  private final RefundCompletionWorker refundCompletionWorker;
  private final RedisLockService redisLockService;

  /**
   * 특정 PROCESSING 환불을 복구한다. 호출자는 동일 환불에 대한 동시 실행을 직렬화해야 한다.
   *
   * @param refundId 환불 ID
   * @return 복구 후 환불 엔티티
   */
  public Refund recoverProcessingRefund(Long refundId) {
    Refund refund = findRefund(refundId);
    if (refund.getStatus() != RefundStatus.PROCESSING) {
      return refund;
    }

    Payment payment = getPayment(refund.getPaymentId());
    String portOnePaymentId = requirePortOnePaymentId(payment);
    PortOnePaymentResponse portOnePayment = portOneClient.getPayment(portOnePaymentId);
    String status = portOnePayment.status();

    if (PORTONE_STATUS_CANCELLED.equals(status)) {
      log.info(
          "Recovering stuck refund as COMPLETED. refundId={}, paymentId={}, portOneStatus={}",
          refundId,
          payment.getId(),
          status);
      return refundCompletionWorker.finalizeRefundCompletion(refundId, payment.getOrderId());
    }

    if (PORTONE_STATUS_PAID.equals(status)) {
      log.info(
          "Recovering stuck refund as APPROVED. refundId={}, paymentId={}, portOneStatus={}",
          refundId,
          payment.getId(),
          status);
      refundCompletionWorker.revertRefundCompletion(refundId);
      return findRefund(refundId);
    }

    log.warn(
        "Unexpected PortOne status while recovering refund. refundId={}, paymentId={}, portOneStatus={}. Reverting to APPROVED for safe retry.",
        refundId,
        payment.getId(),
        status);
    refundCompletionWorker.revertRefundCompletion(refundId);
    return findRefund(refundId);
  }

  /** 오래된 PROCESSING 환불을 찾아 복구한다. */
  public void recoverStaleProcessingRefunds(LocalDateTime staleBefore) {
    List<Refund> stuckRefunds =
        refundRepository.findAllByStatusAndModifiedAtBefore(RefundStatus.PROCESSING, staleBefore);

    if (stuckRefunds.isEmpty()) {
      return;
    }

    log.info("Found {} stale PROCESSING refunds to recover.", stuckRefunds.size());
    stuckRefunds.stream().map(Refund::getId).forEach(this::recoverStaleProcessingRefundSafely);
  }

  private void recoverStaleProcessingRefundSafely(Long refundId) {
    redisLockService.tryExecuteWithLock(
        LockKeys.refund(refundId),
        () -> {
          try {
            recoverProcessingRefund(refundId);
          } catch (RuntimeException e) {
            log.warn("Refund recovery skipped after failure. refundId={}", refundId, e);
          }
        });
  }

  private Refund findRefund(Long refundId) {
    return refundRepository
        .findById(refundId)
        .orElseThrow(() -> new RefundException(ErrorCode.REFUND_NOT_FOUND));
  }

  private Payment getPayment(Long paymentId) {
    return paymentRepository
        .findById(paymentId)
        .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
  }

  private String requirePortOnePaymentId(Payment payment) {
    if (payment.getMerchantPaymentId() == null || payment.getMerchantPaymentId().isBlank()) {
      throw new PaymentException(ErrorCode.PAYMENT_TRANSACTION_ID_REQUIRED);
    }
    return payment.getMerchantPaymentId();
  }
}
