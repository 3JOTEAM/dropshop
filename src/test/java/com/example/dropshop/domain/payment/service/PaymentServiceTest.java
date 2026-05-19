package com.example.dropshop.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.dropshop.common.lock.LockKeys;
import com.example.dropshop.common.lock.RedisLockService;
import com.example.dropshop.domain.auth.sse.service.SseEmitterService;
import com.example.dropshop.domain.dashboard.service.SellerDashboardRefreshService;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.entity.OrderItem;
import com.example.dropshop.domain.order.enums.OrderStatus;
import com.example.dropshop.domain.order.facade.OrderFacadeService;
import com.example.dropshop.domain.payment.client.PortOneClient;
import com.example.dropshop.domain.payment.dto.response.PortOnePaymentResponse;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentMethod;
import com.example.dropshop.domain.payment.enums.PaymentStatus;
import com.example.dropshop.domain.payment.exception.PaymentException;
import com.example.dropshop.domain.payment.outbox.PaymentOutboxPublisher;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

  @Mock private PaymentRepository paymentRepository;

  @Mock private OrderFacadeService orderFacadeService;

  @Mock private PortOneClient portOneClient;

  @Mock private RedisLockService redisLockService;

  @Mock private TransactionTemplate transactionTemplate;

  @Spy
  private PaymentVerificationService paymentVerificationService = new PaymentVerificationService();

  @Mock private PaymentOutboxPublisher paymentOutboxPublisher;

  @Mock private SellerDashboardRefreshService sellerDashboardRefreshService;

  @Mock private SseEmitterService sseEmitterService;

  @InjectMocks private PaymentService paymentService;

  private Order order;
  private Payment payment;

  @BeforeEach
  void setUp() {
    order = Order.create(1L, 10L);
    ReflectionTestUtils.setField(order, "id", 1L);
    ReflectionTestUtils.setField(order, "holdExpiredAt", LocalDateTime.now().plusMinutes(5));
    order.addOrderItem(
        OrderItem.create(
            order,
            100L,
            new BigDecimal("100000"),
            new BigDecimal("79000"),
            new BigDecimal("21000"),
            "https://dummy-image"));

    payment = Payment.prepare(1L, "payment-test-123", PaymentMethod.CARD, new BigDecimal("79000"));
    ReflectionTestUtils.setField(payment, "id", 1L);

    lenient()
        .when(redisLockService.executeWithLock(anyString(), any()))
        .thenAnswer(
            invocation ->
                ((RedisLockService.LockCallback<?>) invocation.getArgument(1)).doInLock());
    lenient()
        .when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
  }

  @Test
  @DisplayName("결제 준비 성공")
  void preparePayment_success() {
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(paymentRepository.existsByOrderId(1L)).willReturn(false);
    given(paymentRepository.findByMerchantPaymentId("payment-test-123"))
        .willReturn(Optional.empty());
    given(paymentRepository.findByOrderId(1L)).willReturn(Optional.empty());
    given(paymentRepository.save(any(Payment.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    Payment result =
        paymentService.preparePayment(
            "test@test.com", 1L, new BigDecimal("79000"), "payment-test-123", PaymentMethod.CARD);

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(result.getOrderId()).isEqualTo(1L);
    verify(paymentRepository, times(1)).save(any(Payment.class));
  }

  @Test
  @DisplayName("결제 준비 실패 - merchantPaymentId가 중복이면 예외가 발생한다")
  void preparePayment_merchantPaymentIdConflict_throwsException() {
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    Payment existingPayment =
        Payment.prepare(999L, "payment-test-123", PaymentMethod.TRANSFER, new BigDecimal("1000"));
    given(paymentRepository.findByMerchantPaymentId("payment-test-123"))
        .willReturn(Optional.of(existingPayment));

    assertThatThrownBy(
            () ->
                paymentService.preparePayment(
                    "test@test.com",
                    1L,
                    new BigDecimal("79000"),
                    "payment-test-123",
                    PaymentMethod.CARD))
        .isInstanceOf(PaymentException.class);

    verify(paymentRepository, never()).save(any(Payment.class));
  }

  @Test
  @DisplayName("결제 준비 재시도 - 같은 merchantPaymentId와 같은 요청이면 기존 결제를 반환한다")
  void preparePayment_sameMerchantPaymentId_returnsExistingPayment() {
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(paymentRepository.findByMerchantPaymentId("payment-test-123"))
        .willReturn(Optional.of(payment));

    Payment result =
        paymentService.preparePayment(
            "test@test.com", 1L, new BigDecimal("79000"), "payment-test-123", PaymentMethod.CARD);

    assertThat(result).isSameAs(payment);
    verify(paymentRepository, never()).save(any(Payment.class));
    verify(paymentRepository, never()).existsByOrderId(any());
  }

  @Test
  @DisplayName("결제 준비 재시도 - 같은 주문의 기존 PENDING 결제가 있으면 merchantPaymentId를 바꾸지 않고 재사용한다")
  void preparePayment_existingPendingPayment_reusesOriginalMerchantPaymentId() {
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(paymentRepository.findByMerchantPaymentId("payment-retry-456")).willReturn(Optional.empty());
    given(paymentRepository.findByOrderId(1L)).willReturn(Optional.of(payment));

    Payment result =
        paymentService.preparePayment(
            "test@test.com", 1L, new BigDecimal("79000"), "payment-retry-456", PaymentMethod.CARD);

    assertThat(result).isSameAs(payment);
    assertThat(result.getMerchantPaymentId()).isEqualTo("payment-test-123");
    verify(paymentRepository, never()).save(any(Payment.class));
    verify(paymentRepository, never()).existsByOrderId(any());
  }

  @Test
  @DisplayName("결제 확정 성공 - PortOne 결제 완료면 Payment와 Order가 완료 상태가 된다")
  void confirmPayment_success() {
    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(orderFacadeService.payOrderByPayment(order))
        .willAnswer(
            invocation -> {
              order.pay();
              return order;
            });
    given(portOneClient.getPayment("payment-test-123"))
        .willReturn(portOnePayment("PAID", "tx-123", "79000"));

    Payment result = paymentService.confirmPayment(1L, "test@test.com", "payment-test-123");

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(result.getPortOneTransactionId()).isEqualTo("tx-123");
    verify(orderFacadeService, times(1)).payOrderByPayment(order);
    verify(orderFacadeService, never()).cancelOrderByPaymentFailure(any(Order.class));
    verify(redisLockService).executeWithLock(eq(LockKeys.order(1L)), any());
    verify(sellerDashboardRefreshService, times(1)).refreshForOrder(order);
    verify(sseEmitterService, times(1)).sendPaymentSuccessNotification(eq(order), anyString());
    verify(paymentOutboxPublisher, times(1)).save(any());
  }

  @Test
  @DisplayName("대시보드 갱신이 실패해도 결제 확정은 유지된다")
  void confirmPayment_dashboardRefreshFailure_doesNotRollbackConfirmation() {
    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(orderFacadeService.payOrderByPayment(order))
        .willAnswer(
            invocation -> {
              order.pay();
              return order;
            });
    given(portOneClient.getPayment("payment-test-123"))
        .willReturn(portOnePayment("PAID", "tx-123", "79000"));
    doThrow(new RuntimeException("dashboard refresh failed"))
        .when(sellerDashboardRefreshService)
        .refreshForOrder(order);

    Payment result = paymentService.confirmPayment(1L, "test@test.com", "payment-test-123");

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    verify(sseEmitterService, times(1)).sendPaymentSuccessNotification(eq(order), anyString());
    verify(paymentOutboxPublisher, times(1)).save(any());
  }

  @Test
  @DisplayName("결제 확정 실패 - PortOne 결제가 완료되지 않으면 주문 취소를 주문 파사드에 위임한다")
  void confirmPayment_failed() {
    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(orderFacadeService.cancelOrderByPaymentFailure(order))
        .willAnswer(
            invocation -> {
              order.cancel();
              return order;
            });
    given(portOneClient.getPayment("payment-test-123"))
        .willReturn(portOnePayment("FAILED", "tx-999", "79000"));

    Payment result = paymentService.confirmPayment(1L, "test@test.com", "payment-test-123");

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
    verify(orderFacadeService, times(1)).cancelOrderByPaymentFailure(order);
    verify(sellerDashboardRefreshService, never()).refreshForOrder(any(Order.class));
    verify(sseEmitterService, times(1)).sendPaymentFailNotification(eq(order), anyString());
    verify(paymentOutboxPublisher, times(1)).save(any());
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
    verify(sellerDashboardRefreshService, never()).refreshForOrder(any(Order.class));
    verify(sseEmitterService, never())
        .sendPaymentSuccessNotification(any(Order.class), anyString());
    verify(sseEmitterService, never()).sendPaymentFailNotification(any(Order.class), anyString());
    verify(paymentOutboxPublisher, never()).save(any());
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

  private PortOnePaymentResponse portOnePayment(
      String status, String transactionId, String amount) {
    return new PortOnePaymentResponse(
        "payment-test-123",
        status,
        transactionId,
        new PortOnePaymentResponse.Amount(new BigDecimal(amount)));
  }
}
