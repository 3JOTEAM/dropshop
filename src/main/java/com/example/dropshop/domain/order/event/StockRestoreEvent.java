package com.example.dropshop.domain.order.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 재고 복원 이벤트.
 */
@Getter
@RequiredArgsConstructor
public class StockRestoreEvent {

  private final Long dropId;
  private final int quantity;
}
