package com.example.dropshop.domain.refund.scheduler;

import com.example.dropshop.domain.refund.service.RefundRecoveryService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 오래된 PROCESSING 환불을 주기적으로 복구한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRecoveryScheduler {

  private static final long STALE_THRESHOLD_MINUTES = 2L;

  private final RefundRecoveryService refundRecoveryService;

  @Scheduled(fixedDelay = 60000)
  @SchedulerLock(
      name = "refundRecoveryScheduler_recoverStaleProcessingRefunds",
      lockAtMostFor = "PT2M",
      lockAtLeastFor = "PT5S")
  public void recoverStaleProcessingRefunds() {
    log.info("PROCESSING 환불 복구 스케줄러 실행");
    refundRecoveryService.recoverStaleProcessingRefunds(
        LocalDateTime.now().minusMinutes(STALE_THRESHOLD_MINUTES));
  }
}
