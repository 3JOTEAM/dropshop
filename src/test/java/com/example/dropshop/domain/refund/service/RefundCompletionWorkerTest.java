package com.example.dropshop.domain.refund.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.entity.OrderItem;
import com.example.dropshop.domain.order.facade.OrderFacadeService;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentMethod;
import com.example.dropshop.domain.payment.exception.PaymentException;
import com.example.dropshop.domain.payment.repository.PaymentRepository;
import com.example.dropshop.domain.refund.entity.Refund;
import com.example.dropshop.domain.refund.entity.RefundStatus;
import com.example.dropshop.domain.refund.repository.RefundRepository;
import java.math.BigDecimal;
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
class RefundCompletionWorkerTest {

  @Mock
  private RefundRepository refundRepository;

  @Mock
  private PaymentRepository paymentRepository;

  @Mock
  private OrderFacadeService orderFacadeService;

  @InjectMocks
  private RefundCompletionWorker refundCompletionWorker;

  private Refund refund;
  private Payment payment;
  private Order order;

  @BeforeEach
  void setUp() {
    refund = Refund.create(1L, new BigDecimal("79000"), "단순 변심");
    ReflectionTestUtils.setField(refund, "id", 1L);
    refund.approve();

    payment = Payment.prepare(1L, "payment-test-123", PaymentMethod.CARD, new BigDecimal("79000"));
    ReflectionTestUtils.setField(payment, "id", 1L);
    payment.complete("tx-123");

    order = Order.create(1L, 10L);
    ReflectionTestUtils.setField(order, "id", 1L);
    order.addOrderItem(OrderItem.create(
        order,
        100L,
        new BigDecimal("100000"),
        new BigDecimal("79000"),
        new BigDecimal("21000"),
        "https://dummy-image"
    ));
    order.pay();
  }

  @Test
  @DisplayName("환불 완료 준비 시 환불을 PROCESSING으로 전이하고 portOneTransactionId를 반환한다")
  void prepareRefundCompletion_success() {
    given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);

    RefundCompletionWorker.RefundCompletionCommand command =
        refundCompletionWorker.prepareRefundCompletion(1L, "test@test.com");

    assertThat(refund.getStatus()).isEqualTo(RefundStatus.PROCESSING);
    assertThat(command.portOneTransactionId()).isEqualTo("tx-123");
    assertThat(command.refundReason()).isEqualTo("단순 변심");
  }

  @Test
  @DisplayName("환불 완료 준비 시 portOneTransactionId가 없으면 예외가 발생한다")
  void prepareRefundCompletion_withoutPortOneTransactionId_throwsException() {
    Payment paymentWithoutPortOneTransactionId =
        Payment.prepare(1L, "payment-test-123", PaymentMethod.CARD, new BigDecimal("79000"));
    ReflectionTestUtils.setField(paymentWithoutPortOneTransactionId, "id", 1L);
    paymentWithoutPortOneTransactionId.complete("tx-123");
    ReflectionTestUtils.setField(paymentWithoutPortOneTransactionId, "portOneTransactionId", " ");

    given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
    given(paymentRepository.findById(1L)).willReturn(Optional.of(paymentWithoutPortOneTransactionId));

    assertThatThrownBy(() -> refundCompletionWorker.prepareRefundCompletion(1L, "test@test.com"))
        .isInstanceOf(PaymentException.class)
        .hasMessage(ErrorCode.PAYMENT_TRANSACTION_ID_REQUIRED.getMessage());
  }

  @Test
  @DisplayName("환불 완료 마감 시 주문과 환불을 완료 상태로 변경한다")
  void finalizeRefundCompletion_success() {
    refund.startProcessing();

    given(refundRepository.findById(1L)).willReturn(Optional.of(refund));
    given(orderFacadeService.findOrderForPaymentWebhook(1L)).willReturn(order);
    given(orderFacadeService.refundOrderByRefund(order)).willAnswer(invocation -> {
      order.refund();
      return order;
    });

    Refund result = refundCompletionWorker.finalizeRefundCompletion(1L, 1L);

    assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
    assertThat(order.getStatus().name()).isEqualTo("REFUNDED");
    verify(orderFacadeService, times(1)).refundOrderByRefund(order);
  }

  @Test
  @DisplayName("외부 PG 환불 실패 복구 시 PROCESSING 환불을 APPROVED로 되돌린다")
  void revertRefundCompletion_success() {
    refund.startProcessing();
    given(refundRepository.findById(1L)).willReturn(Optional.of(refund));

    refundCompletionWorker.revertRefundCompletion(1L);

    assertThat(refund.getStatus()).isEqualTo(RefundStatus.APPROVED);
  }

  @Test
  @DisplayName("이미 완료된 환불 마감은 주문 환불을 다시 호출하지 않는다")
  void finalizeRefundCompletion_completedRefund_noop() {
    refund.startProcessing();
    refund.complete();
    given(refundRepository.findById(1L)).willReturn(Optional.of(refund));

    Refund result = refundCompletionWorker.finalizeRefundCompletion(1L, 1L);

    assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
    verify(orderFacadeService, never()).refundOrderByRefund(order);
  }
}
