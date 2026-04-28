package com.example.dropshop.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.dropshop.common.lock.RedisLockService;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.entity.OrderItem;
import com.example.dropshop.domain.order.enums.OrderStatus;
import com.example.dropshop.domain.order.event.StockRestoreEvent;
import com.example.dropshop.domain.order.exception.OrderException;
import com.example.dropshop.domain.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private RedisLockService redisLockService;

  @Mock
  private TransactionTemplate transactionTemplate;

  @InjectMocks
  private OrderService orderService;

  private Long userId;
  private Long dropId;
  private Long productId;
  private BigDecimal priceSnapshot;
  private BigDecimal salePriceSnapshot;
  private BigDecimal discountAmountSnapshot;
  private String thumbnailUrlSnapshot;

  @BeforeEach
  void setUp() {
    userId = 1L;
    dropId = 10L;
    productId = 100L;
    priceSnapshot = new BigDecimal("100000");
    salePriceSnapshot = new BigDecimal("79000");
    discountAmountSnapshot = new BigDecimal("21000");
    thumbnailUrlSnapshot = "https://dummy-image";

    lenient().when(redisLockService.executeWithLock(anyString(), any())).thenAnswer(
        invocation -> ((RedisLockService.LockCallback<?>) invocation.getArgument(1)).doInLock()
    );
    lenient().when(redisLockService.tryExecuteWithLock(anyString(), any())).thenAnswer(
        invocation -> {
          ((RedisLockService.LockRunnable) invocation.getArgument(1)).doInLock();
          return true;
        }
    );
    lenient().when(transactionTemplate.execute(any())).thenAnswer(
        invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null)
    );
  }

  @Test
  @DisplayName("주문 생성 성공")
  void createOrder_success() {
    given(orderRepository.existsByUserIdAndDropIdAndStatusIn(
        userId,
        dropId,
        List.of(OrderStatus.PENDING, OrderStatus.PAID)
    )).willReturn(false);
    given(orderRepository.save(any(Order.class))).willAnswer(invocation -> {
      Order savedOrder = invocation.getArgument(0);
      ReflectionTestUtils.setField(savedOrder, "id", 1L);
      return savedOrder;
    });

    Order order = orderService.createOrder(
        userId,
        dropId,
        productId,
        priceSnapshot,
        salePriceSnapshot,
        discountAmountSnapshot,
        thumbnailUrlSnapshot
    );

    assertThat(order.getId()).isEqualTo(1L);
    assertThat(order.getUserId()).isEqualTo(userId);
    assertThat(order.getDropId()).isEqualTo(dropId);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.getTotalAmount()).isEqualByComparingTo("79000");
    assertThat(order.getOrderItems()).hasSize(1);

    verify(orderRepository, times(1)).save(any(Order.class));
  }

  @Test
  @DisplayName("중복 주문이면 예외 발생")
  void createOrder_duplicate_throwsException() {
    given(orderRepository.existsByUserIdAndDropIdAndStatusIn(
        userId,
        dropId,
        List.of(OrderStatus.PENDING, OrderStatus.PAID)
    )).willReturn(true);

    assertThatThrownBy(() -> orderService.createOrder(
        userId,
        dropId,
        productId,
        priceSnapshot,
        salePriceSnapshot,
        discountAmountSnapshot,
        thumbnailUrlSnapshot
    )).isInstanceOf(OrderException.class);

    verify(orderRepository, never()).save(any(Order.class));
  }

  @Test
  @DisplayName("주문 단건 조회 성공")
  void findOrderById_success() {
    Order order = Order.create(userId, dropId);
    ReflectionTestUtils.setField(order, "id", 1L);

    given(orderRepository.findByIdAndUserId(1L, userId)).willReturn(Optional.of(order));

    Order result = orderService.findOrderById(1L, userId);

    assertThat(result.getId()).isEqualTo(1L);
    assertThat(result.getUserId()).isEqualTo(userId);
    assertThat(result.getDropId()).isEqualTo(dropId);
  }

  @Test
  @DisplayName("주문 단건 조회 실패 - 주문이 없으면 예외 발생")
  void findOrderById_notFound_throwsException() {
    given(orderRepository.findByIdAndUserId(999L, userId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.findOrderById(999L, userId))
        .isInstanceOf(OrderException.class);
  }

  @Test
  @DisplayName("만료된 주문은 취소되고 재고 복원 이벤트가 발행된다")
  void cancelExpiredOrders_success() {
    Order expiredOrder = createPendingOrder();
    ReflectionTestUtils.setField(expiredOrder, "holdExpiredAt", LocalDateTime.now().minusMinutes(1));

    given(orderRepository.findAllByStatusAndHoldExpiredAtBefore(
        eq(OrderStatus.PENDING),
        any(LocalDateTime.class)
    )).willReturn(List.of(expiredOrder));
    given(orderRepository.findById(1L)).willReturn(Optional.of(expiredOrder));

    orderService.cancelExpiredOrders();

    assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

    ArgumentCaptor<StockRestoreEvent> eventCaptor =
        ArgumentCaptor.forClass(StockRestoreEvent.class);
    verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getDropId()).isEqualTo(dropId);
  }

  @Test
  @DisplayName("주문 목록 조회 성공")
  void findOrdersByUserId_success() {
    Order order1 = Order.create(userId, dropId);
    ReflectionTestUtils.setField(order1, "id", 1L);

    Order order2 = Order.create(userId, 20L);
    ReflectionTestUtils.setField(order2, "id", 2L);

    org.springframework.data.domain.Page<Order> page =
        new org.springframework.data.domain.PageImpl<>(List.of(order1, order2));

    given(orderRepository.findAllByUserIdOrderByCreatedAtDesc(
        eq(userId),
        any(org.springframework.data.domain.Pageable.class)
    )).willReturn(page);

    org.springframework.data.domain.Page<Order> result = orderService.findAllOrdersByUserId(
        userId,
        org.springframework.data.domain.PageRequest.of(0, 20)
    );

    assertThat(result.getContent()).hasSize(2);
  }

  @Test
  @DisplayName("수동 주문 취소 성공 - 상태가 CANCELLED로 변경되고 재고 복원 이벤트가 발행된다")
  void cancelOrder_success() {
    Order order = createPendingOrder();
    given(orderRepository.findByIdAndUserId(1L, userId)).willReturn(Optional.of(order));

    Order result = orderService.cancelOrder(1L, userId);

    assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);

    ArgumentCaptor<StockRestoreEvent> eventCaptor =
        ArgumentCaptor.forClass(StockRestoreEvent.class);
    verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getQuantity()).isEqualTo(1);
  }

  @Test
  @DisplayName("수동 주문 취소 실패 - 주문이 없으면 예외 발생")
  void cancelOrder_notFound_throwsException() {
    given(orderRepository.findByIdAndUserId(999L, userId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.cancelOrder(999L, userId))
        .isInstanceOf(OrderException.class);

    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("수동 주문 취소 실패 - 이미 결제 완료된 주문이면 예외 발생")
  void cancelOrder_invalidStatus_paid_throwsException() {
    Order order = createPendingOrder();
    order.pay();

    given(orderRepository.findByIdAndUserId(1L, userId)).willReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.cancelOrder(1L, userId))
        .isInstanceOf(OrderException.class);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("환불 완료 시 주문 상태가 REFUNDED로 변경되고 재고 복원 이벤트가 발행된다")
  void refundOrder_success() {
    Order order = createPendingOrder();
    order.pay();

    Order result = orderService.refundOrder(order);

    assertThat(result.getStatus()).isEqualTo(OrderStatus.REFUNDED);

    ArgumentCaptor<StockRestoreEvent> eventCaptor =
        ArgumentCaptor.forClass(StockRestoreEvent.class);
    verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getQuantity()).isEqualTo(1);
  }

  @Test
  @DisplayName("만료된 주문이 없으면 아무 일도 일어나지 않는다")
  void cancelExpiredOrders_noExpiredOrders() {
    given(orderRepository.findAllByStatusAndHoldExpiredAtBefore(
        eq(OrderStatus.PENDING),
        any(LocalDateTime.class)
    )).willReturn(List.of());

    orderService.cancelExpiredOrders();

    verify(eventPublisher, never()).publishEvent(any());
  }

  private Order createPendingOrder() {
    Order order = Order.create(userId, dropId);
    ReflectionTestUtils.setField(order, "id", 1L);
    order.addOrderItem(OrderItem.create(
        order,
        productId,
        priceSnapshot,
        salePriceSnapshot,
        discountAmountSnapshot,
        thumbnailUrlSnapshot
    ));
    return order;
  }
}
