package com.example.dropshop.domain.notification.drops.dto.response;

import com.example.dropshop.domain.notification.drops.entity.Drops;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/** 드랍 목록 조회 응답 DTO. */
@Getter
@Builder
public class DropListItemResponse {

  private final Long dropId;
  private final Long productId;
  private final String productName;
  private final String thumbnailUrl;
  private final String status;
  private final LocalDateTime startAt;
  private final LocalDateTime endAt;
  private final Long viewCount;
  private final Long soldCount;
  private final Long remainStock;
  private final Long purchaseLimit;
  private final boolean useQueue;

  /** 드랍 엔티티를 목록 응답 DTO로 변환한다. */
  public static DropListItemResponse from(Drops drops) {
    return DropListItemResponse.builder()
        .dropId(drops.getId())
        .productId(drops.getProduct().getId())
        .productName(drops.getProduct().getName())
        .thumbnailUrl(drops.getProduct().getThumbnailUrl())
        .status(drops.getStatus().name())
        .startAt(drops.getStartAt())
        .endAt(drops.getEndAt())
        .viewCount(drops.getViewCount())
        .soldCount(drops.getTotalStock() - drops.getRemainStock())
        .remainStock(drops.getRemainStock())
        .purchaseLimit(drops.getPurchaseLimit())
        .useQueue(drops.isUseQueue())
        .build();
  }
}
