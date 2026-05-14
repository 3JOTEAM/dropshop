package com.example.dropshop.domain.drops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.service.DropsService;
import com.example.dropshop.domain.notification.drops.service.DropsStatusTransitionService;
import com.example.dropshop.domain.notification.drops.service.DropsStatusTransitionWorker;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DropsStatusTransitionServiceTest {

  @Mock private DropsService dropsService;

  @Mock private DropsStatusTransitionWorker transitionWorker;

  @InjectMocks private DropsStatusTransitionService dropsStatusTransitionService;

  @Test
  @DisplayName("예정 드랍 전이 성공 건수를 반환한다")
  void transitionScheduledToActive_success() {
    LocalDateTime now = LocalDateTime.now();
    Drops scheduled = createDrop(1L, 1L, now.minusMinutes(1), now.plusDays(1));

    given(dropsService.findScheduledDropsToActivate(eq(now))).willReturn(List.of(scheduled));
    given(transitionWorker.transitionScheduledDrop(eq(1L))).willReturn(true);

    int transitioned = dropsStatusTransitionService.transitionScheduledToActive(now);

    assertThat(transitioned).isEqualTo(1);
    verify(dropsService).findScheduledDropsToActivate(eq(now));
    verify(transitionWorker).transitionScheduledDrop(eq(1L));
  }

  @Test
  @DisplayName("예정 드랍 전이 중 동시성 예외가 나면 재시도 후 다음 드랍도 계속 처리한다")
  void transitionScheduledToActive_optimisticLockContinue() {
    LocalDateTime now = LocalDateTime.now();
    Drops first = createDrop(1L, 1L, now.minusMinutes(1), now.plusDays(1));
    Drops second = createDrop(2L, 1L, now.minusMinutes(1), now.plusDays(1));

    given(dropsService.findScheduledDropsToActivate(eq(now))).willReturn(List.of(first, second));
    given(transitionWorker.transitionScheduledDrop(eq(1L)))
        .willThrow(new OptimisticLockingFailureException("conflict-1"))
        .willThrow(new OptimisticLockingFailureException("conflict-2"))
        .willReturn(true);
    given(transitionWorker.transitionScheduledDrop(eq(2L))).willReturn(true);

    int transitioned = dropsStatusTransitionService.transitionScheduledToActive(now);

    assertThat(transitioned).isEqualTo(2);
    verify(dropsService).findScheduledDropsToActivate(eq(now));

    var inOrder = inOrder(transitionWorker);
    inOrder.verify(transitionWorker, times(3)).transitionScheduledDrop(1L);
    inOrder.verify(transitionWorker).transitionScheduledDrop(2L);
  }

  @Test
  @DisplayName("종료 대상 ACTIVE 드랍 전이 성공 건수를 반환한다")
  void transitionActiveToFinished_success() {
    LocalDateTime now = LocalDateTime.now();
    Drops active = createDrop(2L, 1L, now.minusDays(1), now.minusMinutes(1));
    active.activate();

    given(dropsService.findActiveDropsToFinish(eq(now))).willReturn(List.of(active));
    given(transitionWorker.transitionActiveDrop(eq(2L))).willReturn(true);

    int transitioned = dropsStatusTransitionService.transitionActiveToFinished(now);

    assertThat(transitioned).isEqualTo(1);
    verify(dropsService).findActiveDropsToFinish(eq(now));
    verify(transitionWorker).transitionActiveDrop(eq(2L));
  }

  private Drops createDrop(Long dropId, Long sellerId, LocalDateTime startAt, LocalDateTime endAt) {
    Product product =
        Product.create(
            sellerId,
            "테스트 상품",
            "TEST",
            new BigDecimal("100000"),
            10,
            100,
            "https://example.com/thumb.jpg",
            "상품 설명",
            "상품 상세",
            "배송 안내",
            "환불 정책");
    ReflectionTestUtils.setField(product, "id", 100L + dropId);

    Drops drops = Drops.create(product, startAt, endAt, 10L, 1L, false);
    ReflectionTestUtils.setField(drops, "id", dropId);
    return drops;
  }
}
