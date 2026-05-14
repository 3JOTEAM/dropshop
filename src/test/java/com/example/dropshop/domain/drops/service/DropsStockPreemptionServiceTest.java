package com.example.dropshop.domain.drops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import com.example.dropshop.domain.notification.drops.service.DropsService;
import com.example.dropshop.domain.notification.drops.service.DropsStockPreemptionService;
import com.example.dropshop.domain.product.entity.Product;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * DropsStockPreemptionService 단위 테스트.
 *
 * <p>Redis Lua 스크립트 결과값에 따른 재고 선점/보상 분기를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked") // RedisScript<T> 제네릭 Mockito any() 경고 억제
class DropsStockPreemptionServiceTest {

  private static final Long DROP_ID = 1L;
  private static final Long REMAIN_STOCK = 10L;

  @Mock private StringRedisTemplate stringRedisTemplate;

  @Mock private DropsService dropsService;

  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private DropsStockPreemptionService dropsStockPreemptionService;

  private Drops drops;

  @BeforeEach
  void setUp() {
    Product product =
        Product.create(
            1L,
            "한정판 스니커즈",
            "SHOES",
            new BigDecimal("250000"),
            10,
            100,
            "https://cdn.example.com/thumb.jpg",
            "<p>설명</p>",
            "사이즈: 255",
            "배송 안내",
            "환불 정책");
    ReflectionTestUtils.setField(product, "id", 1L);

    drops =
        Drops.create(
            product,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(1),
            REMAIN_STOCK,
            1L,
            true);
    ReflectionTestUtils.setField(drops, "id", DROP_ID);
    ReflectionTestUtils.setField(drops, "status", DropsStatus.ACTIVE);
  }

  // ─────────────────────────────────────────────────────────
  // tryReserve: 재고 선점 성공
  // ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("Redis 키가 존재하고 재고가 충분하면 선점에 성공한다")
  void tryReserve_success_whenStockSufficient() {
    // Lua 스크립트 결과: 선점 후 남은 재고 = 9
    given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
        .willReturn(9L);

    boolean result = dropsStockPreemptionService.tryReserve(DROP_ID, 1);

    assertThat(result).isTrue();
    verify(dropsService, never()).findById(DROP_ID);
  }

  @Test
  @DisplayName("선점 후 남은 재고가 0이어도 선점에 성공한다")
  void tryReserve_success_whenStockBecomesZero() {
    // 재고가 딱 1개 남아있어 선점 후 0이 되는 경우
    given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
        .willReturn(0L);

    boolean result = dropsStockPreemptionService.tryReserve(DROP_ID, 1);

    assertThat(result).isTrue();
  }

  // ─────────────────────────────────────────────────────────
  // tryReserve: 재고 부족으로 실패
  // ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("Redis 재고가 부족하면 선점에 실패한다 (Lua 결과 -1)")
  void tryReserve_fail_whenStockInsufficient() {
    // Lua 스크립트 결과: 재고 부족 = -1
    given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
        .willReturn(-1L);

    boolean result = dropsStockPreemptionService.tryReserve(DROP_ID, 5);

    assertThat(result).isFalse();
    verify(dropsService, never()).findById(DROP_ID);
  }

  // ─────────────────────────────────────────────────────────
  // tryReserve: 키 미존재 → fail-close
  // ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("Redis 키가 없으면 주문 선점은 fail-close 처리한다")
  void tryReserve_keyMissing_failClose() {
    given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
        .willReturn(-2L);

    boolean result = dropsStockPreemptionService.tryReserve(DROP_ID, 1);

    assertThat(result).isFalse();
    verify(dropsService, never()).findById(DROP_ID);
  }

  @Test
  @DisplayName("정의되지 않은 음수 결과는 선점 실패로 처리한다")
  void tryReserve_fail_whenUnexpectedNegativeResult() {
    given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
        .willReturn(-3L);

    boolean result = dropsStockPreemptionService.tryReserve(DROP_ID, 1);

    assertThat(result).isFalse();
    verify(dropsService, never()).findById(DROP_ID);
  }

  // ─────────────────────────────────────────────────────────
  // tryReserve: Redis execute null 반환
  // ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("Redis execute가 null을 반환하면 선점에 실패한다")
  void tryReserve_fail_whenRedisReturnsNull() {
    given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
        .willReturn(null);

    boolean result = dropsStockPreemptionService.tryReserve(DROP_ID, 1);

    assertThat(result).isFalse();
  }

  // ─────────────────────────────────────────────────────────
  // increaseStock: 보상/복구 재고 증가
  // ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("Redis 키가 존재할 때 compensateReservedStock은 INCRBY만 호출한다")
  void compensateReservedStock_success_whenKeyExists() {
    // INCREASE_IF_EXISTS_SCRIPT 결과: 1 (키 존재 + 증가 성공)
    given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
        .willReturn(1L);

    dropsStockPreemptionService.compensateReservedStock(DROP_ID, 1);

    // DB 초기화 없이 Redis만 update
    verify(dropsService, never()).findById(DROP_ID);
  }

  @Test
  @DisplayName("Redis 키가 없을 때 compensateReservedStock은 DB를 조회하지 않는다")
  void compensateReservedStock_skip_whenKeyMissing() {
    // INCREASE_IF_EXISTS_SCRIPT 결과: 0 (키 없음)
    given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
        .willReturn(0L);
    dropsStockPreemptionService.compensateReservedStock(DROP_ID, 1);

    verify(dropsService, never()).findById(DROP_ID);
  }

  @Test
  @DisplayName("Redis 키가 없을 때 increaseStockAfterRestore는 DB 기준으로 키를 적재한다")
  void increaseStockAfterRestore_reinitialize_whenKeyMissing() {
    given(stringRedisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
        .willReturn(0L);
    given(dropsService.findById(DROP_ID)).willReturn(drops);
    given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .willReturn(true);

    dropsStockPreemptionService.increaseStockAfterRestore(DROP_ID, 1);

    verify(dropsService).findById(DROP_ID);
    verify(valueOperations)
        .setIfAbsent(
            eq("stock:drop:" + DROP_ID), eq(String.valueOf(REMAIN_STOCK)), any(Duration.class));
  }

  @Test
  @DisplayName("ACTIVE 전환 선적재 시 preloadStockKey는 SET NX를 사용한다")
  void preloadStockKey_usesSetIfAbsent() {
    given(dropsService.findById(DROP_ID)).willReturn(drops);
    given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
        .willReturn(true);

    dropsStockPreemptionService.preloadStockKey(DROP_ID);

    verify(valueOperations)
        .setIfAbsent(
            eq("stock:drop:" + DROP_ID), eq(String.valueOf(REMAIN_STOCK)), any(Duration.class));
  }
}
