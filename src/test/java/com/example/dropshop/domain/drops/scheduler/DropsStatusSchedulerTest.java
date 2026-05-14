package com.example.dropshop.domain.drops.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.dropshop.domain.notification.drops.scheduler.DropsSchedulerProperties;
import com.example.dropshop.domain.notification.drops.scheduler.DropsStatusScheduler;
import com.example.dropshop.domain.notification.drops.service.DropsStatusTransitionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DropsStatusSchedulerTest {

  @Mock private DropsStatusTransitionService dropsStatusTransitionService;

  @Mock private DropsSchedulerProperties dropsSchedulerProperties;

  @InjectMocks private DropsStatusScheduler dropsStatusScheduler;

  @Test
  @DisplayName("스케줄러 비활성화 시 상태 전이 서비스를 호출하지 않는다")
  void transitionDropsStatus_disabled_skip() {
    org.mockito.BDDMockito.given(dropsSchedulerProperties.isEnabled()).willReturn(false);

    dropsStatusScheduler.transitionDropsStatus();

    verify(dropsStatusTransitionService, never()).transitionScheduledToActive(any());
    verify(dropsStatusTransitionService, never()).transitionActiveToFinished(any());
  }

  @Test
  @DisplayName("스케줄러 활성화 시 상태 전이 서비스를 순차 호출한다")
  void transitionDropsStatus_enabled_callsServices() {
    org.mockito.BDDMockito.given(dropsSchedulerProperties.isEnabled()).willReturn(true);

    dropsStatusScheduler.transitionDropsStatus();

    verify(dropsStatusTransitionService).transitionScheduledToActive(any());
    verify(dropsStatusTransitionService).transitionActiveToFinished(any());
  }
}
