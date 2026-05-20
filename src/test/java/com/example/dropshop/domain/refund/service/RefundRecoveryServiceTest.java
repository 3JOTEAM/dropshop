package com.example.dropshop.domain.refund.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.dropshop.common.lock.RedisLockService;
import com.example.dropshop.domain.payment.client.PortOneClient;
import com.example.dropshop.domain.payment.dto.response.PortOnePaymentResponse;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentMethod;
import com.example.dropshop.domain.payment.repository.PaymentRepository;
import com.example.dropshop.domain.refund.entity.Refund;
import com.example.dropshop.domain.refund.enums.RefundStatus;
import com.example.dropshop.domain.refund.repository.RefundRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefundRecoveryServiceTest {

  @Mock private RefundRepository refundRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private PortOneClient portOneClient;
  @Mock private RefundCompletionWorker refundCompletionWorker;
  @Mock private RedisLockService redisLockService;

  @InjectMocks private RefundRecoveryService refundRecoveryService;

  private Refund refund;
  private Payment payment;

  @BeforeEach
  void setUp() {
    refund = Refund.create(1L, new BigDecimal("79000"), "단순 변심");
    ReflectionTestUtils.setField(refund, "id", 1L);
    refund.approve();
    refund.startProcessing();

    payment = Payment.prepare(1L, "payment-test-123", PaymentMethod.CARD, new BigDecimal("79000"));
    ReflectionTestUtils.setField(payment, "id", 1L);
    payment.complete("tx-123");

    lenient()
        .when(redisLockService.tryExecuteWithLock(anyString(), any()))
        .thenAnswer(
            invocation -> {
              ((RedisLockService.LockRunnable) invocation.getArgument(1)).doInLock();
              return true;
            });
  }

  @Test
  @DisplayName("PortOne 상태가 CANCELLED면 PROCESSING 환불을 완료 처리한다")
  void recoverProcessingRefund_cancelled_finalizesRefund() {
    Refund completedRefund = Refund.create(1L, new BigDecimal("79000"), "단순 변심");
    ReflectionTestUtils.setField(completedRefund, "id", 1L);
    completedRefund.approve();
    completedRefund.startProcessing();
    completedRefund.complete();

    given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(portOneClient.getPayment("payment-test-123"))
        .willReturn(portOnePayment("CANCELLED", "tx-123", "79000"));
    given(refundCompletionWorker.finalizeRefundCompletion(1L, 1L)).willReturn(completedRefund);

    Refund result = refundRecoveryService.recoverProcessingRefund(1L);

    assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
    verify(refundCompletionWorker, times(1)).finalizeRefundCompletion(1L, 1L);
    verify(refundCompletionWorker, never()).revertRefundCompletion(1L);
  }

  @Test
  @DisplayName("PortOne 상태가 PAID면 PROCESSING 환불을 APPROVED로 되돌린다")
  void recoverProcessingRefund_paid_revertsRefund() {
    Refund approvedRefund = Refund.create(1L, new BigDecimal("79000"), "단순 변심");
    ReflectionTestUtils.setField(approvedRefund, "id", 1L);
    approvedRefund.approve();

    given(refundRepository.findById(1L))
        .willReturn(Optional.of(refund), Optional.of(approvedRefund));
    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(portOneClient.getPayment("payment-test-123"))
        .willReturn(portOnePayment("PAID", "tx-123", "79000"));

    Refund result = refundRecoveryService.recoverProcessingRefund(1L);

    assertThat(result.getStatus()).isEqualTo(RefundStatus.APPROVED);
    verify(refundCompletionWorker, times(1)).revertRefundCompletion(1L);
    verify(refundCompletionWorker, never()).finalizeRefundCompletion(1L, 1L);
  }

  @Test
  @DisplayName("오래된 PROCESSING 환불을 스케줄러 복구 대상으로 조회한다")
  void recoverStaleProcessingRefunds_processesCandidates() {
    LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(2);

    given(refundRepository.findAllByStatusAndModifiedAtBefore(RefundStatus.PROCESSING, staleBefore))
        .willReturn(List.of(refund));
    given(refundRepository.findById(1L)).willReturn(Optional.of(refund), Optional.of(refund));
    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(portOneClient.getPayment("payment-test-123"))
        .willReturn(portOnePayment("PAID", "tx-123", "79000"));

    refundRecoveryService.recoverStaleProcessingRefunds(staleBefore);

    verify(refundCompletionWorker, times(1)).revertRefundCompletion(1L);
  }

  private PortOnePaymentResponse portOnePayment(
      String status, String transactionId, String amount) {
    return new PortOnePaymentResponse(
        "payment-test-123",
        status,
        transactionId,
        new PortOnePaymentResponse.Amount(new BigDecimal(amount)));
  }
}
