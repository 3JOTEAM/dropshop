package com.example.dropshop.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.dropshop.common.lock.RedisLockService;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.entity.OrderItem;
import com.example.dropshop.domain.order.facade.OrderFacadeService;
import com.example.dropshop.domain.payment.client.PortOneClient;
import com.example.dropshop.domain.payment.dto.response.PortOnePaymentResponse;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentMethod;
import com.example.dropshop.domain.payment.enums.PaymentStatus;
import com.example.dropshop.domain.payment.exception.PaymentException;
import com.example.dropshop.domain.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock
  private PaymentRepository paymentRepository;

  @Mock
  private OrderFacadeService orderFacadeService;

  @Mock
  private PortOneClient portOneClient;

  @Mock
  private RedisLockService redisLockService;

  @Mock
  private TransactionTemplate transactionTemplate;

  @InjectMocks
  private PaymentService paymentService;

  private Order order;
  private Payment payment;

  @BeforeEach
  void setUp() {
    order = Order.create(1L, 10L);
    ReflectionTestUtils.setField(order, "id", 1L);
    ReflectionTestUtils.setField(order, "holdExpiredAt", LocalDateTime.now().plusMinutes(5));
    order.addOrderItem(OrderItem.create(
        order,
        100L,
        new BigDecimal("100000"),
        new BigDecimal("79000"),
        new BigDecimal("21000"),
        "https://dummy-image"
    ));

    payment = Payment.prepare(1L, "payment-test-123", PaymentMethod.CARD, new BigDecimal("79000"));
    ReflectionTestUtils.setField(payment, "id", 1L);

    lenient().when(redisLockService.executeWithLock(anyString(), any())).thenAnswer(
        invocation -> ((RedisLockService.LockCallback<?>) invocation.getArgument(1)).doInLock()
    );
    lenient().when(transactionTemplate.execute(any())).thenAnswer(
        invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null)
    );
  }

  @Test
  @DisplayName("결제 준비 성공")
  void preparePayment_success() {
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(paymentRepository.existsByOrderId(1L)).willReturn(false);
    given(paymentRepository.existsByMerchantPaymentId("payment-test-123")).willReturn(false);
    given(paymentRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

    Payment result = paymentService.preparePayment(
        "test@test.com",
        1L,
        new BigDecimal("79000"),
        "payment-test-123",
        PaymentMethod.CARD
    );

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(result.getOrderId()).isEqualTo(1L);
    verify(paymentRepository, times(1)).save(any(Payment.class));
  }

  @Test
  @DisplayName("결제 준비 실패 - merchantPaymentId가 중복이면 예외가 발생한다")
  void preparePayment_merchantPaymentIdConflict_throwsException() {
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(paymentRepository.existsByOrderId(1L)).willReturn(false);
    given(paymentRepository.existsByMerchantPaymentId("payment-test-123")).willReturn(true);

    assertThatThrownBy(() -> paymentService.preparePayment(
        "test@test.com",
        1L,
        new BigDecimal("79000"),
        "payment-test-123",
        PaymentMethod.CARD
    )).isInstanceOf(PaymentException.class);

    verify(paymentRepository, never()).save(any(Payment.class));
  }

  @Test
  @DisplayName("결제 확정 성공 - PortOne 결제 완료면 Payment와 Order가 완료 상태가 된다")
  void confirmPayment_success() {
    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(orderFacadeService.payOrderByPayment(order)).willAnswer(invocation -> {
      order.pay();
      return order;
    });
    given(portOneClient.getPayment("payment-test-123")).willReturn(portOnePayment("PAID", "tx-123", "79000"));

    Payment result = paymentService.confirmPayment(1L, "test@test.com", "payment-test-123");

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(result.getPortOneTransactionId()).isEqualTo("tx-123");
    verify(orderFacadeService, times(1)).payOrderByPayment(order);
    verify(orderFacadeService, never()).cancelOrderByPaymentFailure(any(Order.class));
  }

  @Test
  @DisplayName("결제 확정 실패 - PortOne 결제가 완료되지 않으면 주문 취소를 주문 파사드에 위임한다")
  void confirmPayment_failed() {
    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(orderFacadeService.cancelOrderByPaymentFailure(order)).willAnswer(invocation -> {
      order.cancel();
      return order;
    });
    given(portOneClient.getPayment("payment-test-123")).willReturn(portOnePayment("FAILED", "tx-999", "79000"));

    Payment result = paymentService.confirmPayment(1L, "test@test.com", "payment-test-123");

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
    verify(orderFacadeService, times(1)).cancelOrderByPaymentFailure(order);
  }

  @Test
  @DisplayName("결제 확정은 이미 완료된 결제에 대해 멱등하게 현재 상태를 반환한다")
  void confirmPayment_alreadyCompleted_returnsCurrentState() {
    payment.complete("tx-123");
    order.pay();

    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);

    Payment result = paymentService.confirmPayment(1L, "test@test.com", "payment-test-123");

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    verify(portOneClient, never()).getPayment(any());
    verify(orderFacadeService, never()).cancelOrderByPaymentFailure(any(Order.class));
  }

  @Test
  @DisplayName("결제 확정 실패 - PortOne paymentId가 내부 결제 요청 키와 다르면 예외가 발생한다")
  void confirmPayment_mismatch() {
    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);

    assertThatThrownBy(() -> paymentService.confirmPayment(1L, "test@test.com", "payment-other"))
        .isInstanceOf(PaymentException.class);

    verify(portOneClient, never()).getPayment(any());
  }

  private PortOnePaymentResponse portOnePayment(String status, String transactionId, String amount) {
    return new PortOnePaymentResponse(
        "payment-test-123",
        status,
        transactionId,
        new PortOnePaymentResponse.Amount(new BigDecimal(amount))
    );
  }
}
