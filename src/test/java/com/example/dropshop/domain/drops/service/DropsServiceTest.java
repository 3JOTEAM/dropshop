package com.example.dropshop.domain.drops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.notification.drops.dto.request.DropCreateRequest;
import com.example.dropshop.domain.notification.drops.dto.request.DropUpdateRequest;
import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import com.example.dropshop.domain.notification.drops.exception.DropsException;
import com.example.dropshop.domain.notification.drops.repository.DropsRepository;
import com.example.dropshop.domain.notification.drops.service.DropsService;
import com.example.dropshop.domain.product.entity.Product;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DropsServiceTest {

  @Mock private DropsRepository dropsRepository;

  @InjectMocks private DropsService dropsService;

  @Test
  @DisplayName("드랍 생성 성공")
  void create_success() {
    // given
    Product product = createProduct(1L, 100);
    DropCreateRequest request =
        createDropCreateRequest(
            product.getId(),
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(2),
            30L,
            1L,
            true);

    given(dropsRepository.save(any(Drops.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    // when
    Drops saved = dropsService.create(product, request);

    // then
    assertThat(saved.getProduct()).isEqualTo(product);
    assertThat(saved.getStatus()).isEqualTo(DropsStatus.SCHEDULED);
    assertThat(saved.getTotalStock()).isEqualTo(30L);
    assertThat(saved.getRemainStock()).isEqualTo(30L);
    assertThat(saved.getPurchaseLimit()).isEqualTo(1L);
    assertThat(saved.isUseQueue()).isTrue();
  }

  @Test
  @DisplayName("진행 중인 드랍은 시작 시간과 총 판매 수량 수정이 불가능하다")
  void update_activeDropStartAtOrTotalStock_throwsException() {
    // given
    Drops drops = createDrop(createProduct(1L, 100), 20L, 20L);
    drops.activate();
    DropUpdateRequest request =
        createDropUpdateRequest(LocalDateTime.now().plusDays(3), null, null, null, null);

    // when & then
    assertThatThrownBy(() -> dropsService.update(drops, 100, request))
        .isInstanceOf(DropsException.class)
        .hasMessage(ErrorCode.DROP_ACTIVE_UPDATE_LOCKED.getMessage());
  }

  @Test
  @DisplayName("이미 판매된 수량보다 작은 총 판매 수량으로는 수정할 수 없다")
  void update_totalStockLessThanSoldCount_throwsException() {
    // given
    Drops drops = createDrop(createProduct(1L, 100), 10L, 8L);
    DropUpdateRequest request =
        createDropUpdateRequest(null, LocalDateTime.now().plusDays(3), 1L, 1L, false);

    // when & then
    assertThatThrownBy(() -> dropsService.update(drops, 100, request))
        .isInstanceOf(DropsException.class)
        .hasMessage(ErrorCode.INVALID_DROP_REMAIN_STOCK.getMessage());
  }

  @Test
  @DisplayName("종료 대상 ACTIVE 드랍은 단일 쿼리로 조회한다")
  void findActiveDropsToFinish_success() {
    // given
    LocalDateTime baseTime = LocalDateTime.now();
    Drops activeDrop = createDrop(createProduct(1L, 100), 10L, 5L);
    given(dropsRepository.findAllActiveDropsToFinish(DropsStatus.ACTIVE, baseTime))
        .willReturn(List.of(activeDrop));

    // when
    List<Drops> result = dropsService.findActiveDropsToFinish(baseTime);

    // then
    assertThat(result).hasSize(1);
    assertThat(result.get(0)).isEqualTo(activeDrop);
  }

  private Product createProduct(Long sellerId, int stock) {
    Product product =
        Product.create(
            sellerId,
            "한정판 스니커즈",
            "SHOES",
            new BigDecimal("250000"),
            10,
            stock,
            "https://cdn.example.com/thumb.jpg",
            "<p>상품 설명</p>",
            "사이즈: 255",
            "배송 안내",
            "환불 정책");
    ReflectionTestUtils.setField(product, "id", 1L);
    return product;
  }

  private Drops createDrop(Product product, Long totalStock, Long remainStock) {
    Drops drops =
        Drops.create(
            product,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(2),
            totalStock,
            1L,
            true);
    ReflectionTestUtils.setField(drops, "remainStock", remainStock);
    return drops;
  }

  private DropCreateRequest createDropCreateRequest(
      Long productId,
      LocalDateTime startAt,
      LocalDateTime endAt,
      Long totalStock,
      Long purchaseLimit,
      boolean useQueue) {
    DropCreateRequest request = new DropCreateRequest();
    ReflectionTestUtils.setField(request, "productId", productId);
    ReflectionTestUtils.setField(request, "startAt", startAt);
    ReflectionTestUtils.setField(request, "endAt", endAt);
    ReflectionTestUtils.setField(request, "totalStock", totalStock);
    ReflectionTestUtils.setField(request, "purchaseLimit", purchaseLimit);
    ReflectionTestUtils.setField(request, "useQueue", useQueue);
    return request;
  }

  private DropUpdateRequest createDropUpdateRequest(
      LocalDateTime startAt,
      LocalDateTime endAt,
      Long totalStock,
      Long purchaseLimit,
      Boolean useQueue) {
    DropUpdateRequest request = new DropUpdateRequest();
    ReflectionTestUtils.setField(request, "startAt", startAt);
    ReflectionTestUtils.setField(request, "endAt", endAt);
    ReflectionTestUtils.setField(request, "totalStock", totalStock);
    ReflectionTestUtils.setField(request, "purchaseLimit", purchaseLimit);
    ReflectionTestUtils.setField(request, "useQueue", useQueue);
    return request;
  }
}
