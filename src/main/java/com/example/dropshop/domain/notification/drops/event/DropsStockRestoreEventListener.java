package com.example.dropshop.domain.notification.drops.event;

import com.example.dropshop.domain.notification.drops.service.DropsFacadeService;
import com.example.dropshop.domain.order.event.StockRestoreEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 주문 취소/결제 실패로 발생한 재고 복구 이벤트를 드랍 도메인에 반영한다. */
@Component
@RequiredArgsConstructor
public class DropsStockRestoreEventListener {

  private final DropsFacadeService dropsFacadeService;

  /**
   * 재고 복구 이벤트를 수신해 드랍 잔여 재고를 복원한다.
   *
   * @param event 주문 도메인에서 발행된 재고 복구 이벤트
   */
  @SuppressWarnings("checkstyle:MissingJavadocMethod")
  @EventListener
  public void handle(StockRestoreEvent event) {
    dropsFacadeService.restoreStockForOrder(event.getDropId(), event.getQuantity());
  }
}
