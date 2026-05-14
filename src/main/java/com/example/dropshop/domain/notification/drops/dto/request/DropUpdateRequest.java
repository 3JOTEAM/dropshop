package com.example.dropshop.domain.notification.drops.dto.request;

import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import lombok.Getter;

/** 드랍 수정 요청 DTO. */
@Getter
public class DropUpdateRequest {

  private LocalDateTime startAt;

  private LocalDateTime endAt;

  @Positive private Long totalStock;

  @Positive private Long purchaseLimit;

  private Boolean useQueue;
}
