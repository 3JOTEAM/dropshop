package com.example.dropshop.domain.drops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.lock.RedisLockService;
import com.example.dropshop.domain.notification.drops.dto.request.DropCreateRequest;
import com.example.dropshop.domain.notification.drops.dto.response.DropResponse;
import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import com.example.dropshop.domain.notification.drops.exception.DropsException;
import com.example.dropshop.domain.notification.drops.service.DropsFacadeService;
import com.example.dropshop.domain.notification.drops.service.DropsService;
import com.example.dropshop.domain.notification.drops.service.DropsStockPreemptionService;
import com.example.dropshop.domain.order.service.OrderHistoryQueryService;
import com.example.dropshop.domain.product.entity.Product;
import com.example.dropshop.domain.product.enums.ProductStatus;
import com.example.dropshop.domain.product.service.ProductDomainFacadeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class DropsFacadeServiceTest {

  private static final Long DEFAULT_DROP_ID = 10L;
  private static final Long DEFAULT_PRODUCT_ID = 1L;

  @Mock private DropsService dropsService;

  @Mock private ProductDomainFacadeService productDomainFacadeService;

  @Mock private OrderHistoryQueryService orderHistoryQueryService;

  @Mock private RedisLockService redisLockService;

  @Mock private DropsStockPreemptionService dropsStockPreemptionService;

  @InjectMocks private DropsFacadeService dropsFacadeService;

  @BeforeEach
  void setUp() {
    lenient()
        .when(redisLockService.executeWithLock(anyString(), any()))
        .thenAnswer(
            invocation -> {
              RedisLockService.LockCallback<?> callback = invocation.getArgument(1);
              return callback.doInLock();
            });
  }

  @Test
  @DisplayName("판매자 드랍 생성 성공 시 상품 상태를 READY로 변경한다")
  void createSellerDrop_success() {
    LocalDateTime now = LocalDateTime.now();
    Product product = createProduct(1L);
    Drops drops = createScheduledDrop(product, now);
    DropCreateRequest request = createDropCreateRequest(product.getId(), now);

    given(productDomainFacadeService.findOwnedProduct(product.getId(), 1L)).willReturn(product);
    given(dropsService.existsOngoingDropForProduct(product.getId())).willReturn(false);
    given(dropsService.create(product, request)).willReturn(drops);

    DropResponse response = dropsFacadeService.createSellerDrop(1L, true, true, request);

    assertThat(response.getProductId()).isEqualTo(product.getId());
    assertThat(response.getStatus()).isEqualTo("SCHEDULED");
    verify(productDomainFacadeService).updateStatusByDrop(product, ProductStatus.READY);
  }

  @Test
  @DisplayName("주문 이력이 있는 드랍은 삭제할 수 없다")
  void deleteSellerDrop_withOrderHistory_throwsException() {
    LocalDateTime now = LocalDateTime.now();
    Product product = createProduct(1L);
    Drops drops = createScheduledDrop(product, now);

    given(dropsService.findById(DEFAULT_DROP_ID)).willReturn(drops);
    given(orderHistoryQueryService.existsOrderHistoryForDrop(DEFAULT_DROP_ID)).willReturn(true);

    assertThatThrownBy(() -> dropsFacadeService.deleteSellerDrop(DEFAULT_DROP_ID, 1L, true, true))
        .isInstanceOf(DropsException.class)
        .hasMessage(ErrorCode.DROP_DELETE_NOT_ALLOWED.getMessage());

    verify(dropsService, never()).delete(any(Drops.class));
    verify(productDomainFacadeService, never())
        .updateStatusByDrop(any(Product.class), any(ProductStatus.class));
  }

  @Test
  @DisplayName("판매자 드랍 강제 종료 성공 시 상품 상태를 OUT_OF_STOCK으로 변경한다")
  void stopSellerDrop_success() {
    LocalDateTime now = LocalDateTime.now();
    Product product = createProduct(1L);
    Drops drops = createActiveDrop(product, now);

    given(dropsService.findById(DEFAULT_DROP_ID)).willReturn(drops);

    DropResponse response = dropsFacadeService.stopSellerDrop(DEFAULT_DROP_ID, 1L, true, true);

    assertThat(response.getStatus()).isEqualTo("FINISHED");
    verify(productDomainFacadeService).updateStatusByDrop(product, ProductStatus.OUT_OF_STOCK);
  }

  @Test
  @DisplayName("주문 생성 시 ACTIVE 드랍 재고가 차감된다")
  void reserveStockForOrder_success() {
    LocalDateTime now = LocalDateTime.now();
    Product product = createProduct(1L);
    Drops drops = createActiveDrop(product, now);
    ReflectionTestUtils.setField(drops, "remainStock", 5L);

    given(dropsStockPreemptionService.tryReserve(DEFAULT_DROP_ID, 1)).willReturn(true);
    given(dropsService.findById(DEFAULT_DROP_ID)).willReturn(drops);

    Drops result = dropsFacadeService.reserveStockForOrder(DEFAULT_DROP_ID, 1L, 1);

    assertThat(result.getRemainStock()).isEqualTo(4L);
    verify(dropsStockPreemptionService, never()).compensateReservedStock(DEFAULT_DROP_ID, 1);
    verify(productDomainFacadeService, never())
        .updateStatusByDrop(any(Product.class), any(ProductStatus.class));
  }

  @Test
  @DisplayName("Redis 선점에 실패하면 재고 차감 없이 예외를 던진다")
  void reserveStockForOrder_redisReserveFail_throwsException() {
    given(dropsStockPreemptionService.tryReserve(DEFAULT_DROP_ID, 1)).willReturn(false);

    assertThatThrownBy(() -> dropsFacadeService.reserveStockForOrder(DEFAULT_DROP_ID, 1L, 1))
        .isInstanceOf(DropsException.class)
        .hasMessage(ErrorCode.INVALID_DROP_REMAIN_STOCK.getMessage());

    verify(dropsService, never()).findById(DEFAULT_DROP_ID);
    verify(dropsStockPreemptionService, never()).compensateReservedStock(DEFAULT_DROP_ID, 1);
  }

  @Test
  @DisplayName("선점 후 예외가 발생하면 롤백 훅에서 Redis 보상을 수행한다")
  void reserveStockForOrder_productMismatch_compensateRedisOnRollbackHook() {
    LocalDateTime now = LocalDateTime.now();
    Product product = createProduct(1L);
    Drops drops = createActiveDrop(product, now);

    given(dropsStockPreemptionService.tryReserve(DEFAULT_DROP_ID, 1)).willReturn(true);
    given(dropsService.findById(DEFAULT_DROP_ID)).willReturn(drops);

    TransactionSynchronizationManager.initSynchronization();
    try {
      assertThatThrownBy(() -> dropsFacadeService.reserveStockForOrder(DEFAULT_DROP_ID, 999L, 1))
          .isInstanceOf(DropsException.class)
          .hasMessage(ErrorCode.DROP_PRODUCT_MISMATCH.getMessage());

      assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
      for (TransactionSynchronization synchronization :
          TransactionSynchronizationManager.getSynchronizations()) {
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
      }
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }

    verify(dropsStockPreemptionService).compensateReservedStock(DEFAULT_DROP_ID, 1);
  }

  @Test
  @DisplayName("드랍 종료 상태에서 재고 복원 시 ACTIVE로 재전환된다")
  void restoreStockForOrder_reactivateSuccess() {
    LocalDateTime now = LocalDateTime.now();
    Product product = createProduct(1L);
    Drops drops = createFinishedButReactivatableDrop(product, now);
    ReflectionTestUtils.setField(drops, "remainStock", 0L);

    given(dropsService.findById(DEFAULT_DROP_ID)).willReturn(drops);

    dropsFacadeService.restoreStockForOrder(DEFAULT_DROP_ID, 1);

    assertThat(drops.isActive()).isTrue();
    assertThat(drops.getRemainStock()).isEqualTo(1L);
    verify(productDomainFacadeService).updateStatusByDrop(product, ProductStatus.ON_SALE);
  }

  @Test
  @DisplayName("재고 복원 후 재활성화 중 동시성 충돌이 발생해도 예외를 전파하지 않는다")
  void restoreStockForOrder_optimisticLockIgnore() {
    LocalDateTime now = LocalDateTime.now();
    Product product = createProduct(1L);
    Drops drops = createFinishedButReactivatableDrop(product, now);
    ReflectionTestUtils.setField(drops, "remainStock", 0L);

    given(dropsService.findById(DEFAULT_DROP_ID)).willReturn(drops);
    doThrow(new OptimisticLockingFailureException("conflict"))
        .when(productDomainFacadeService)
        .updateStatusByDrop(product, ProductStatus.ON_SALE);

    dropsFacadeService.restoreStockForOrder(DEFAULT_DROP_ID, 1);

    assertThat(drops.getRemainStock()).isEqualTo(1L);
    verify(productDomainFacadeService).updateStatusByDrop(product, ProductStatus.ON_SALE);
  }

  private Product createProduct(Long sellerId) {
    Product product =
        Product.create(
            sellerId,
            "한정판 스니커즈",
            "SHOES",
            new BigDecimal("250000"),
            10,
            100,
            "https://cdn.example.com/thumb.jpg",
            "<p>상품 설명</p>",
            "사이즈: 255",
            "배송 안내",
            "환불 정책");
    ReflectionTestUtils.setField(product, "id", DEFAULT_PRODUCT_ID);
    return product;
  }

  private Drops createDrop(
      Product product, LocalDateTime startAt, LocalDateTime endAt, DropsStatus status) {
    Drops drops = Drops.create(product, startAt, endAt, 30L, 1L, true);
    ReflectionTestUtils.setField(drops, "status", status);
    ReflectionTestUtils.setField(drops, "id", DEFAULT_DROP_ID);
    return drops;
  }

  private Drops createScheduledDrop(Product product, LocalDateTime baseTime) {
    return createDrop(product, baseTime.plusDays(1), baseTime.plusDays(2), DropsStatus.SCHEDULED);
  }

  private Drops createActiveDrop(Product product, LocalDateTime baseTime) {
    return createDrop(product, baseTime.minusHours(1), baseTime.plusHours(1), DropsStatus.ACTIVE);
  }

  private Drops createFinishedButReactivatableDrop(Product product, LocalDateTime baseTime) {
    return createDrop(product, baseTime.minusDays(1), baseTime.plusHours(2), DropsStatus.FINISHED);
  }

  private DropCreateRequest createDropCreateRequest(Long productId, LocalDateTime baseTime) {
    DropCreateRequest request = new DropCreateRequest();
    ReflectionTestUtils.setField(request, "productId", productId);
    ReflectionTestUtils.setField(request, "startAt", baseTime.plusDays(1));
    ReflectionTestUtils.setField(request, "endAt", baseTime.plusDays(2));
    ReflectionTestUtils.setField(request, "totalStock", 30L);
    ReflectionTestUtils.setField(request, "purchaseLimit", 1L);
    ReflectionTestUtils.setField(request, "useQueue", true);
    return request;
  }
}
