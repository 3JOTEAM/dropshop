package com.example.dropshop.domain.notification.drops.event;

import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** 드랍 상태 변경 이벤트. */
@Getter
@Builder
public class DropStatusChangedEvent {

  private final Long dropId;
  private final Long productId;
  private final DropsStatus fromStatus;
  private final DropsStatus toStatus;
  private final String cause;
  private final LocalDateTime occurredAt;
}
