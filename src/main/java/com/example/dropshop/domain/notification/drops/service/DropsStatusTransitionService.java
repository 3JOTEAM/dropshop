package com.example.dropshop.domain.notification.drops.service;

import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.exception.DropsException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/** 드랍 상태 자동 전이 서비스. */
@Service
@Slf4j
@RequiredArgsConstructor
public class DropsStatusTransitionService {

  private static final int MAX_RETRY_ATTEMPTS = 3;
  private static final long BASE_BACKOFF_MILLIS = 50L;
  private static final long JITTER_BOUND_MILLIS = 30L;

  private final DropsService dropsService;
  private final DropsStatusTransitionWorker transitionWorker;

  /** 시작 시간이 된 드랍을 ACTIVE로 전이한다. */
  public int transitionScheduledToActive(LocalDateTime baseTime) {
    List<Drops> scheduledDrops = dropsService.findScheduledDropsToActivate(baseTime);
    int transitionedCount = 0;
    for (Drops drops : scheduledDrops) {
      if (executeWithRetry(
          () -> transitionWorker.transitionScheduledDrop(drops.getId()),
          drops.getId(),
          "SCHEDULED_TO_ACTIVE")) {
        transitionedCount++;
      }
    }
    return transitionedCount;
  }

  /** 종료 조건을 만족한 드랍을 FINISHED로 전이한다. */
  public int transitionActiveToFinished(LocalDateTime baseTime) {
    List<Drops> activeDrops = dropsService.findActiveDropsToFinish(baseTime);
    int transitionedCount = 0;
    for (Drops drops : activeDrops) {
      if (executeWithRetry(
          () -> transitionWorker.transitionActiveDrop(drops.getId()),
          drops.getId(),
          "ACTIVE_TO_FINISHED")) {
        transitionedCount++;
      }
    }
    return transitionedCount;
  }

  private boolean executeWithRetry(
      Supplier<Boolean> transitionAction, Long dropId, String transitionName) {
    for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
      try {
        return transitionAction.get();
      } catch (OptimisticLockingFailureException e) {
        if (attempt == MAX_RETRY_ATTEMPTS) {
          log.warn(
              "드랍 ID={} {} 전이가 동시성 충돌로 최종 실패했습니다. attempts={}", dropId, transitionName, attempt, e);
          return false;
        }
        if (!waitBeforeRetry(attempt, dropId, transitionName)) {
          return false;
        }
        log.info(
            "드랍 ID={} {} 전이 동시성 충돌, 재시도합니다. attempt={}/{}",
            dropId,
            transitionName,
            attempt,
            MAX_RETRY_ATTEMPTS);
      } catch (DropsException e) {
        log.warn("드랍 상태 전이 스킵: 존재하지 않는 드랍입니다. dropId={}, transition={}", dropId, transitionName);
        return false;
      } catch (Exception e) {
        log.error("드랍 ID={} {} 전이 중 예기치 않은 오류가 발생했습니다.", dropId, transitionName, e);
        return false;
      }
    }
    return false;
  }

  private boolean waitBeforeRetry(int attempt, Long dropId, String transitionName) {
    long jitter = ThreadLocalRandom.current().nextLong(JITTER_BOUND_MILLIS + 1);
    long waitMillis = (attempt * BASE_BACKOFF_MILLIS) + jitter;
    try {
      Thread.sleep(waitMillis);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("드랍 ID={} {} 전이 재시도 대기 중 인터럽트가 발생했습니다.", dropId, transitionName, e);
      return false;
    }
  }
}
