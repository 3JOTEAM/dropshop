package com.example.dropshop.domain.notification.drops.service;

import com.example.dropshop.domain.notification.drops.entity.Drops;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Redis 기반 드랍 재고 선점 서비스. */
@Service
@Slf4j
@RequiredArgsConstructor
public class DropsStockPreemptionService {

  private static final long RESERVE_RESULT_INSUFFICIENT = -1L;
  private static final long RESERVE_RESULT_KEY_MISSING = -2L;

  private static final DefaultRedisScript<Long> RESERVE_SCRIPT;
  private static final DefaultRedisScript<Long> INCREASE_IF_EXISTS_SCRIPT;

  static {
    RESERVE_SCRIPT = new DefaultRedisScript<>();
    RESERVE_SCRIPT.setScriptText(
        "local current = redis.call('get', KEYS[1])\n"
            + "if not current then\n"
            + "  return -2\n"
            + "end\n"
            + "current = tonumber(current)\n"
            + "local quantity = tonumber(ARGV[1])\n"
            + "if current < quantity then\n"
            + "  return -1\n"
            + "end\n"
            + "return redis.call('decrby', KEYS[1], quantity)");
    RESERVE_SCRIPT.setResultType(Long.class);

    INCREASE_IF_EXISTS_SCRIPT = new DefaultRedisScript<>();
    INCREASE_IF_EXISTS_SCRIPT.setScriptText(
        "if redis.call('exists', KEYS[1]) == 1 then\n"
            + "  redis.call('incrby', KEYS[1], ARGV[1])\n"
            + "  return 1\n"
            + "end\n"
            + "return 0");
    INCREASE_IF_EXISTS_SCRIPT.setResultType(Long.class);
  }

  private final StringRedisTemplate stringRedisTemplate;
  private final DropsService dropsService;

  /**
   * 주문 수량만큼 재고를 선점한다.
   *
   * <p>주문 핫패스에서는 KEY_MISSING 재초기화를 수행하지 않고 fail-close 처리한다. 재초기화는 ACTIVE 전환 선적재/복구 후 반영 경로에서만 수행해
   * 기준 재고를 단일화한다.
   */
  public boolean tryReserve(Long dropId, int quantity) {
    validatePositiveQuantity(quantity);
    Long reserveResult = executeReserve(dropId, quantity);
    if (reserveResult == null) {
      return false;
    }
    if (reserveResult >= 0L) {
      return true;
    }
    if (reserveResult == RESERVE_RESULT_INSUFFICIENT) {
      return false;
    }

    if (reserveResult == RESERVE_RESULT_KEY_MISSING) {
      log.warn("Redis 재고 키 없음으로 주문 선점을 fail-close 처리합니다. dropId={}", dropId);
      return false;
    }

    return false;
  }

  /**
   * 주문 선점 후 트랜잭션 롤백 시 Redis 선점량을 보상한다.
   *
   * <p>키가 없으면 덮어쓰지 않고 다음 요청의 lazy init에 맡긴다.
   */
  public void compensateReservedStock(Long dropId, int quantity) {
    validatePositiveQuantity(quantity);
    Long increased = executeIncreaseIfExists(dropId, quantity);
    if (!Long.valueOf(1L).equals(increased)) {
      log.debug("Redis 보상 스킵: 키가 없거나 증가 실패. dropId={}, quantity={}", dropId, quantity);
    }
  }

  /**
   * 주문 취소/실패 복구가 커밋된 뒤 Redis 재고를 반영한다.
   *
   * <p>키가 존재하면 INCRBY를 수행하고, 키가 없으면 DB 스냅샷으로 안전하게 선적재한다.
   */
  public void increaseStockAfterRestore(Long dropId, int quantity) {
    validatePositiveQuantity(quantity);
    Long increased = executeIncreaseIfExists(dropId, quantity);
    if (Long.valueOf(1L).equals(increased)) {
      return;
    }

    initializeStockKeyIfAbsent(dropId);
  }

  /** 드랍 ACTIVE 전환 시 Redis 재고 키를 미리 적재한다. */
  public void preloadStockKey(Long dropId) {
    initializeStockKeyIfAbsent(dropId);
  }

  private Long executeReserve(Long dropId, int quantity) {
    return stringRedisTemplate.execute(
        RESERVE_SCRIPT, Collections.singletonList(stockKey(dropId)), String.valueOf(quantity));
  }

  private Long executeIncreaseIfExists(Long dropId, int quantity) {
    return stringRedisTemplate.execute(
        INCREASE_IF_EXISTS_SCRIPT,
        Collections.singletonList(stockKey(dropId)),
        String.valueOf(quantity));
  }

  private void initializeStockKeyIfAbsent(Long dropId) {
    Drops drops = dropsService.findById(dropId);
    Duration ttl = calculateKeyTtl(drops);
    if (ttl.isZero() || ttl.isNegative()) {
      log.debug("Redis 재고 키 미적재: 이미 종료된 드랍입니다. dropId={}", dropId);
      return;
    }

    stringRedisTemplate
        .opsForValue()
        .setIfAbsent(stockKey(dropId), String.valueOf(drops.getRemainStock()), ttl);
  }

  private Duration calculateKeyTtl(Drops drops) {
    LocalDateTime now = LocalDateTime.now();
    if (!drops.getEndAt().isAfter(now)) {
      return Duration.ZERO;
    }
    return Duration.between(now, drops.getEndAt()).plusSeconds(1);
  }

  private String stockKey(Long dropId) {
    return "stock:drop:" + dropId;
  }

  private void validatePositiveQuantity(int quantity) {
    if (quantity <= 0) {
      throw new IllegalArgumentException("quantity must be greater than 0");
    }
  }
}
