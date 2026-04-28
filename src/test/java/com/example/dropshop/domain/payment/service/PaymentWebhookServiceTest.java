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

import com.example.dropshop.common.config.PortOneProperties;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.lock.RedisLockService;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.entity.OrderItem;
import com.example.dropshop.domain.order.exception.OrderException;
import com.example.dropshop.domain.order.facade.OrderFacadeService;
import com.example.dropshop.domain.payment.client.PortOneClient;
import com.example.dropshop.domain.payment.dto.request.PaymentWebhookRequest;
import com.example.dropshop.domain.payment.dto.response.PortOnePaymentResponse;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentMethod;
import com.example.dropshop.domain.payment.enums.PaymentStatus;
import com.example.dropshop.domain.payment.exception.PaymentException;
import com.example.dropshop.domain.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
class PaymentWebhookServiceTest {

  @Mock
  private PaymentRepository paymentRepository;

  @Mock
  private OrderFacadeService orderFacadeService;

  @Mock
  private PortOneClient portOneClient;

  @Mock
  private PortOneProperties portOneProperties;

  @Mock
  private RedisLockService redisLockService;

  @Mock
  private TransactionTemplate transactionTemplate;

  @InjectMocks
  private PaymentWebhookService paymentWebhookService;

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
  @DisplayName("웹훅 처리 성공 - PortOne 결제 완료면 Payment와 Order가 완료 상태가 된다")
  void handleWebhook_success() {
    given(paymentRepository.findByMerchantPaymentId("payment-test-123")).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPaymentWebhook(1L)).willReturn(order);
    given(orderFacadeService.payOrderByPayment(order)).willAnswer(invocation -> {
      order.pay();
      return order;
    });
    given(portOneClient.getPayment("payment-test-123")).willReturn(portOnePayment("PAID", "tx-webhook", "79000"));

    Payment result = paymentWebhookService.handleWebhook("payment-test-123");

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(result.getPortOneTransactionId()).isEqualTo("tx-webhook");
    verify(orderFacadeService, times(1)).payOrderByPayment(order);
  }

  @Test
  @DisplayName("웹훅 재수신 - 이미 완료된 결제면 그대로 유지된다")
  void handleWebhook_idempotent() {
    payment.complete("tx-123");
    order.pay();

    given(paymentRepository.findByMerchantPaymentId("payment-test-123")).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPaymentWebhook(1L)).willReturn(order);
    given(portOneClient.getPayment("payment-test-123")).willReturn(portOnePayment("PAID", "tx-123", "79000"));

    Payment result = paymentWebhookService.handleWebhook("payment-test-123");

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    verify(orderFacadeService, never()).payOrderByPayment(any(Order.class));
    verify(orderFacadeService, never()).cancelOrderByPaymentFailure(any(Order.class));
  }

  @Test
  @DisplayName("웹훅 실패 상태는 결제만 실패 처리하고 주문은 즉시 취소하지 않는다")
  void handleWebhook_failedStatus_failsPaymentOnly() {
    given(paymentRepository.findByMerchantPaymentId("payment-test-123")).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPaymentWebhook(1L)).willReturn(order);
    given(portOneClient.getPayment("payment-test-123")).willReturn(portOnePayment("FAILED", "tx-failed", "79000"));

    Payment result = paymentWebhookService.handleWebhook("payment-test-123");

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
    verify(orderFacadeService, never()).cancelOrderByPaymentFailure(any(Order.class));
  }

  @Test
  @DisplayName("웹훅 요청 처리 전 PortOne 서명을 검증한다")
  void handleWebhook_requestVerifiesSignature() throws Exception {
    PaymentWebhookRequest request = signedWebhookRequest();

    given(portOneProperties.resolvedWebhookSecret()).willReturn("webhook-secret");
    given(paymentRepository.findByMerchantPaymentId("payment-test-123")).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPaymentWebhook(1L)).willReturn(order);
    given(portOneClient.getPayment("payment-test-123")).willReturn(portOnePayment("FAILED", "tx-failed", "79000"));

    Payment result = paymentWebhookService.handleWebhook(request);

    assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
  }

  @Test
  @DisplayName("웹훅 서명이 유효하지 않으면 예외가 발생한다")
  void handleWebhook_invalidSignature_throwsException() {
    PaymentWebhookRequest request = new PaymentWebhookRequest();
    ReflectionTestUtils.setField(request, "id", "payment-test-123");
    ReflectionTestUtils.setField(request, "signatureHash", "invalid");

    given(portOneProperties.resolvedWebhookSecret()).willReturn("webhook-secret");

    assertThatThrownBy(() -> paymentWebhookService.handleWebhook(request))
        .isInstanceOf(PaymentException.class)
        .hasMessage(ErrorCode.PAYMENT_WEBHOOK_INVALID_SIGNATURE.getMessage());

    verify(paymentRepository, never()).findByMerchantPaymentId(any());
  }

  @Test
  @DisplayName("웹훅 결제 식별자가 비어 있으면 웹훅 전용 예외가 발생한다")
  void handleWebhook_blankPaymentId_throwsException() {
    assertThatThrownBy(() -> paymentWebhookService.handleWebhook(" "))
        .isInstanceOf(PaymentException.class)
        .hasMessage(ErrorCode.PAYMENT_WEBHOOK_PAYMENT_ID_REQUIRED.getMessage());

    verify(portOneClient, never()).getPayment(any());
  }

  @Test
  @DisplayName("웹훅 대상 결제가 없으면 웹훅 전용 예외가 발생한다")
  void handleWebhook_paymentNotFound_throwsException() {
    given(paymentRepository.findByMerchantPaymentId("payment-test-123")).willReturn(Optional.empty());

    assertThatThrownBy(() -> paymentWebhookService.handleWebhook("payment-test-123"))
        .isInstanceOf(PaymentException.class)
        .hasMessage(ErrorCode.PAYMENT_WEBHOOK_PAYMENT_NOT_FOUND.getMessage());

    verify(portOneClient, never()).getPayment(any());
  }

  @Test
  @DisplayName("웹훅 PAID 처리 시 주문 홀드가 만료되었으면 예외가 발생한다")
  void handleWebhook_paidButOrderExpired_throwsException() {
    ReflectionTestUtils.setField(order, "holdExpiredAt", LocalDateTime.now().minusMinutes(1));

    given(paymentRepository.findByMerchantPaymentId("payment-test-123")).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPaymentWebhook(1L)).willReturn(order);
    given(portOneClient.getPayment("payment-test-123")).willReturn(portOnePayment("PAID", "tx-expired", "79000"));

    assertThatThrownBy(() -> paymentWebhookService.handleWebhook("payment-test-123"))
        .isInstanceOf(OrderException.class);
  }

  @Test
  @DisplayName("웹훅 PortOne 조회 실패는 예외가 전파된다")
  void handleWebhook_portOneError_throwsException() {
    given(paymentRepository.findByMerchantPaymentId("payment-test-123")).willReturn(Optional.of(payment));
    given(orderFacadeService.findOrderForPaymentWebhook(1L)).willReturn(order);
    given(portOneClient.getPayment("payment-test-123"))
        .willThrow(new PaymentException(ErrorCode.PAYMENT_PORTONE_API_ERROR));

    assertThatThrownBy(() -> paymentWebhookService.handleWebhook("payment-test-123"))
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

  private PaymentWebhookRequest signedWebhookRequest() throws Exception {
    PaymentWebhookRequest request = new PaymentWebhookRequest();
    ReflectionTestUtils.setField(request, "id", "payment-test-123");
    ReflectionTestUtils.setField(request, "amount", new BigDecimal("79000"));
    ReflectionTestUtils.setField(request, "currency", "KRW");
    ReflectionTestUtils.setField(request, "status", "FAILED");
    ReflectionTestUtils.setField(request, "channelKey", "channel-test");
    ReflectionTestUtils.setField(request, "channelOrderRef", "channel-order");
    ReflectionTestUtils.setField(request, "countryCode", "KR");
    ReflectionTestUtils.setField(request, "merchantOrderRef", "merchant-order");
    ReflectionTestUtils.setField(request, "methodName", "CARD");
    ReflectionTestUtils.setField(request, "orderRef", "payment-test-123");
    ReflectionTestUtils.setField(
        request,
        "signatureHash",
        signature("webhook-secret",
            "amount=79000&channel_key=channel-test&channel_order_ref=channel-order"
                + "&country_code=KR&currency=KRW&merchant_order_ref=merchant-order"
                + "&method_name=CARD&order_ref=payment-test-123&status=FAILED")
    );
    return request;
  }

  private String signature(String secret, String message) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getEncoder()
        .encodeToString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
  }
}
