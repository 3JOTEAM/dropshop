package com.example.dropshop.domain.notification.drops.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import lombok.Getter;

/** 드랍 생성 요청 DTO. */
@Getter
public class DropCreateRequest {

  @NotNull private Long productId;

  @NotNull private LocalDateTime startAt;

  @NotNull private LocalDateTime endAt;

  @NotNull @Positive private Long totalStock;

  @NotNull @Positive private Long purchaseLimit;

  @NotNull private Boolean useQueue;
}
