package com.example.dropshop.domain.payment.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.entity.OrderItem;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentMethod;
import com.example.dropshop.domain.payment.service.PaymentQueryService;
import com.example.dropshop.domain.payment.service.PaymentService;
import com.example.dropshop.domain.payment.service.PaymentWebhookService;
import com.example.dropshop.domain.product.entity.Product;
import com.example.dropshop.domain.product.service.ProductDomainFacadeService;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PaymentFacadeServiceTest {

  @Mock
  private PaymentService paymentService;

  @Mock
  private PaymentQueryService paymentQueryService;

  @Mock
  private PaymentWebhookService paymentWebhookService;

  @Mock
  private ProductDomainFacadeService productDomainFacadeService;

  @InjectMocks
  private PaymentFacadeService paymentFacadeService;

  @Test
  @DisplayName("PortOne 요청 정보 조회 시 단일 상품 주문은 상품명을 orderName으로 반환한다")
  void getPortOneRequest_singleItemUsesProductName() {
    Order order = createOrderWithItems(100L);
    Payment payment = createPayment();
    Product product = createProduct(100L, "한정판 스니커즈");

    given(paymentQueryService.getPayment(1L, "test@test.com")).willReturn(payment);
    given(paymentQueryService.getOrder(1L, "test@test.com")).willReturn(order);
    given(paymentQueryService.getStoreId()).willReturn("store-test");
    given(paymentQueryService.getChannelKey()).willReturn("channel-test");
    given(paymentQueryService.getRedirectUrl()).willReturn("http://localhost:8080/payments/redirect");
    given(productDomainFacadeService.findProduct(100L)).willReturn(product);

    var response = paymentFacadeService.getPortOneRequest(1L, "test@test.com");

    assertThat(response.getOrderName()).isEqualTo("한정판 스니커즈");
    verify(productDomainFacadeService, times(1)).findProduct(100L);
  }

  @Test
  @DisplayName("PortOne 요청 정보 조회 시 여러 주문 아이템이 있어도 첫 상품명만 orderName으로 반환한다")
  void getPortOneRequest_multipleItemsUsesFirstProductName() {
    Order order = createOrderWithItems(100L, 200L, 300L);
    Payment payment = createPayment();
    Product firstProduct = createProduct(100L, "한정판 스니커즈");

    given(paymentQueryService.getPayment(1L, "test@test.com")).willReturn(payment);
    given(paymentQueryService.getOrder(1L, "test@test.com")).willReturn(order);
    given(paymentQueryService.getStoreId()).willReturn("store-test");
    given(paymentQueryService.getChannelKey()).willReturn("channel-test");
    given(paymentQueryService.getRedirectUrl()).willReturn("http://localhost:8080/payments/redirect");
    given(productDomainFacadeService.findProduct(100L)).willReturn(firstProduct);

    var response = paymentFacadeService.getPortOneRequest(1L, "test@test.com");

    assertThat(response.getOrderName()).isEqualTo("한정판 스니커즈");
    verify(productDomainFacadeService, times(1)).findProduct(100L);
  }

  private Order createOrderWithItems(Long... productIds) {
    Order order = Order.create(1L, 10L);
    ReflectionTestUtils.setField(order, "id", 1L);

    for (Long productId : productIds) {
      order.addOrderItem(OrderItem.create(
          order,
          productId,
          new BigDecimal("100000"),
          new BigDecimal("79000"),
          new BigDecimal("21000"),
          "https://dummy-image"
      ));
    }
    return order;
  }

  private Payment createPayment() {
    Payment payment = Payment.prepare(
        1L,
        "payment-test-123",
        PaymentMethod.CARD,
        new BigDecimal("79000")
    );
    ReflectionTestUtils.setField(payment, "id", 1L);
    return payment;
  }

  private Product createProduct(Long productId, String name) {
    Product product = Product.create(
        1L,
        name,
        "SHOES",
        new BigDecimal("100000"),
        21,
        100,
        "https://dummy-image",
        "상품 설명",
        "상품 스펙",
        "배송 안내",
        "환불 정책"
    );
    ReflectionTestUtils.setField(product, "id", productId);
    return product;
  }
}
