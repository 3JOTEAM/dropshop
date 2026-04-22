package com.example.dropshop.domain.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.dropshop.common.config.PortOneProperties;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.entity.OrderItem;
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
import java.time.LocalDateTime;
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
class PaymentServiceTest {

  @Mock
  private PaymentRepository paymentRepository;

  @Mock
  private OrderFacadeService orderFacadeService;

  @Mock
  private PortOneClient portOneClient;

  @Mock
  private PortOneProperties portOneProperties;

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
  }

  @Test
  @DisplayName("결제 준비 성공")
  void preparePayment_success() {
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(paymentRepository.existsByOrderId(1L)).willReturn(false);
    given(paymentRepository.existsByIdempotencyKey("payment-test-123")).willReturn(false);
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
  @DisplayName("결제 준비 실패 - 주문 금액과 결제 금액이 다르면 예외가 발생한다")
  void preparePayment_amountMismatch_throwsException() {
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);

    assertThatThrownBy(() -> paymentService.preparePayment(
        "test@test.com",
        1L,
        new BigDecimal("80000"),
        "payment-test-123",
        PaymentMethod.CARD
    )).isInstanceOf(PaymentException.class);

    verify(paymentRepository, never()).save(any(Payment.class));
  }

  @Test
  @DisplayName("결제 준비 실패 - 이미 결제가 존재하면 예외가 발생한다")
  void preparePayment_alreadyExists_throwsException() {
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(paymentRepository.existsByOrderId(1L)).willReturn(true);

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
  @DisplayName("결제 준비 실패 - idempotencyKey가 중복이면 예외가 발생한다")
  void preparePayment_idempotencyKeyConflict_throwsException() {
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(paymentRepository.existsByOrderId(1L)).willReturn(false);
    given(paymentRepository.existsByIdempotencyKey("payment-test-123")).willReturn(true);

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
    assertThat(result.getTransactionId()).isEqualTo("tx-123");
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
  @DisplayName("결제 확정 실패 - PortOne paymentId가 내부 결제 요청 키와 다르면 예외가 발생한다")
  void confirmPayment_mismatch() {
    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);

    assertThatThrownBy(() -> paymentService.confirmPayment(1L, "test@test.com", "payment-other"))
        .isInstanceOf(PaymentException.class);

    verify(portOneClient, never()).getPayment(any());
  }

  @Test
  @DisplayName("결제 확정 실패 - PortOne 응답 금액이 내부 결제 금액과 다르면 예외가 발생한다")
  void confirmPayment_portOneAmountMismatch_throwsException() {
    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(portOneClient.getPayment("payment-test-123")).willReturn(portOnePayment("PAID", "tx-123", "80000"));

    assertThatThrownBy(() -> paymentService.confirmPayment(1L, "test@test.com", "payment-test-123"))
        .isInstanceOf(PaymentException.class);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  @DisplayName("결제 확정 실패 - PortOne 응답 상태가 완료/실패가 아니면 예외가 발생한다")
  void confirmPayment_notFinalStatus_throwsException() {
    given(paymentRepository.findById(1L)).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPayment(1L, "test@test.com")).willReturn(order);
    given(portOneClient.getPayment("payment-test-123")).willReturn(portOnePayment("READY", null, "79000"));

    assertThatThrownBy(() -> paymentService.confirmPayment(1L, "test@test.com", "payment-test-123"))
        .isInstanceOf(PaymentException.class);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
  }

  @Test
  @DisplayName("웹훅 처리 성공 - PortOne 결제 완료면 Payment와 Order가 완료 상태가 된다")
  void handleWebhook_success() {
    given(paymentRepository.findByIdempotencyKey("payment-test-123")).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPaymentWebhook(1L)).willReturn(order);
    given(orderFacadeService.payOrderByPayment(order)).willAnswer(invocation -> {
      order.pay();
      return order;
    });
    given(portOneClient.getPayment("payment-test-123")).willReturn(portOnePayment("PAID", "tx-webhook", "79000"));

    Payment result = paymentService.handleWebhook("payment-test-123");

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(result.getTransactionId()).isEqualTo("tx-webhook");
    verify(orderFacadeService, times(1)).payOrderByPayment(order);
  }

  @Test
  @DisplayName("웹훅 재수신 - 이미 완료된 결제면 그대로 유지된다")
  void handleWebhook_idempotent() {
    payment.complete("tx-123");
    order.pay();

    given(paymentRepository.findByIdempotencyKey("payment-test-123")).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPaymentWebhook(1L)).willReturn(order);
    given(portOneClient.getPayment("payment-test-123")).willReturn(portOnePayment("PAID", "tx-123", "79000"));

    Payment result = paymentService.handleWebhook("payment-test-123");

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    verify(orderFacadeService, never()).payOrderByPayment(any(Order.class));
    verify(orderFacadeService, never()).cancelOrderByPaymentFailure(any(Order.class));
  }

  @Test
  @DisplayName("웹훅 실패 상태는 주문과 결제를 즉시 취소하지 않는다")
  void handleWebhook_failedStatus_doesNotCancelOrder() {
    given(paymentRepository.findByIdempotencyKey("payment-test-123")).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPaymentWebhook(1L)).willReturn(order);
    given(portOneClient.getPayment("payment-test-123")).willReturn(portOnePayment("FAILED", "tx-failed", "79000"));

    Payment result = paymentService.handleWebhook("payment-test-123");

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
    verify(orderFacadeService, never()).cancelOrderByPaymentFailure(any(Order.class));
  }

  @Test
  @DisplayName("웹훅 결제 식별자가 비어 있으면 웹훅 전용 예외가 발생한다")
  void handleWebhook_blankPaymentId_throwsException() {
    assertThatThrownBy(() -> paymentService.handleWebhook(" "))
        .isInstanceOf(PaymentException.class)
        .hasMessage(ErrorCode.PAYMENT_WEBHOOK_PAYMENT_ID_REQUIRED.getMessage());

    verify(portOneClient, never()).getPayment(any());
  }

  @Test
  @DisplayName("웹훅 대상 결제가 없으면 웹훅 전용 예외가 발생한다")
  void handleWebhook_paymentNotFound_throwsException() {
    given(paymentRepository.findByIdempotencyKey("payment-test-123")).willReturn(Optional.empty());

    assertThatThrownBy(() -> paymentService.handleWebhook("payment-test-123"))
        .isInstanceOf(PaymentException.class)
        .hasMessage(ErrorCode.PAYMENT_WEBHOOK_PAYMENT_NOT_FOUND.getMessage());

    verify(portOneClient, never()).getPayment(any());
  }

  @Test
  @DisplayName("웹훅 PAID 처리 시 주문 홀드가 만료되었으면 예외가 발생한다")
  void handleWebhook_paidButOrderExpired_throwsException() {
    ReflectionTestUtils.setField(order, "holdExpiredAt", LocalDateTime.now().minusMinutes(1));

    given(paymentRepository.findByIdempotencyKey("payment-test-123")).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPaymentWebhook(1L)).willReturn(order);
    given(portOneClient.getPayment("payment-test-123")).willReturn(portOnePayment("PAID", "tx-expired", "79000"));

    assertThatThrownBy(() -> paymentService.handleWebhook("payment-test-123"))
        .isInstanceOf(OrderException.class);
  }

  @Test
  @DisplayName("웹훅 PortOne 조회 실패는 예외가 전파된다")
  void handleWebhook_portOneError_throwsException() {
    given(paymentRepository.findByIdempotencyKey("payment-test-123")).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPaymentWebhook(1L)).willReturn(order);
    given(portOneClient.getPayment("payment-test-123"))
        .willThrow(new PaymentException(ErrorCode.PAYMENT_PORTONE_API_ERROR));

    assertThatThrownBy(() -> paymentService.handleWebhook("payment-test-123"))
        .isInstanceOf(PaymentException.class);
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
