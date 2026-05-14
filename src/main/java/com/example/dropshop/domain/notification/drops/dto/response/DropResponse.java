package com.example.dropshop.domain.notification.drops.dto.response;

import com.example.dropshop.domain.notification.drops.entity.Drops;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** 드랍 응답 DTO. */
@Getter
@Builder
public class DropResponse {

  private final Long dropId;
  private final Long productId;
  private final String status;
  private final LocalDateTime startAt;
  private final LocalDateTime endAt;
  private final Long totalStock;
  private final Long remainStock;
  private final Long viewCount;
  private final Long purchaseLimit;
  private final boolean useQueue;
  private final LocalDateTime createdAt;
  private final LocalDateTime modifiedAt;

  /** 드랍 엔티티를 응답 DTO로 변환한다. */
  public static DropResponse from(Drops drops) {
    return from(drops, drops.getViewCount());
  }

  /** 드랍 엔티티를 응답 DTO로 변환한다. */
  public static DropResponse from(Drops drops, Long viewCount) {
    return DropResponse.builder()
        .dropId(drops.getId())
        .productId(drops.getProduct().getId())
        .status(drops.getStatus().name())
        .startAt(drops.getStartAt())
        .endAt(drops.getEndAt())
        .totalStock(drops.getTotalStock())
        .remainStock(drops.getRemainStock())
        .viewCount(viewCount)
        .purchaseLimit(drops.getPurchaseLimit())
        .useQueue(drops.isUseQueue())
        .createdAt(drops.getCreatedAt())
        .modifiedAt(drops.getModifiedAt())
        .build();
  }
}
