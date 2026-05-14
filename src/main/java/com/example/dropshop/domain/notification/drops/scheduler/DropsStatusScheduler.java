package com.example.dropshop.domain.notification.drops.scheduler;

import com.example.dropshop.domain.notification.drops.service.DropsStatusTransitionService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 드랍 상태 자동 전이 스케줄러. */
@Component
@RequiredArgsConstructor
public class DropsStatusScheduler {

  private static final Logger log = LoggerFactory.getLogger(DropsStatusScheduler.class);

  private final DropsStatusTransitionService dropsStatusTransitionService;
  private final DropsSchedulerProperties dropsSchedulerProperties;

  /** 드랍 상태 자동 전이 작업을 주기적으로 실행한다. */
  @Scheduled(fixedDelayString = "${drops.scheduler.fixed-delay-millis:30000}")
  @SchedulerLock(
      name = "dropsStatusScheduler_transitionDropsStatus",
      lockAtMostFor = "${drops.scheduler.lock-at-most-for:PT1M}",
      lockAtLeastFor = "${drops.scheduler.lock-at-least-for:PT1S}")
  public void transitionDropsStatus() {
    if (!dropsSchedulerProperties.isEnabled()) {
      log.debug("[DropsStatusScheduler] 스케줄러 비활성화 상태입니다.");
      return;
    }

    LocalDateTime now = LocalDateTime.now();
    int activatedCount = dropsStatusTransitionService.transitionScheduledToActive(now);
    int finishedCount = dropsStatusTransitionService.transitionActiveToFinished(now);

    if (activatedCount > 0 || finishedCount > 0) {
      log.info(
          "[DropsStatusScheduler] 상태 전이 완료 - activated: {}, finished: {}",
          activatedCount,
          finishedCount);
      return;
    }
    log.debug("[DropsStatusScheduler] 전이 대상 없음");
  }
}
