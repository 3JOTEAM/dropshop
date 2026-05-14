package com.example.dropshop.domain.order.event;

import com.example.dropshop.domain.order.producer.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 주문 도메인 이벤트를 커밋 이후 Kafka로 전달한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaEventListener {

  private final OrderEventProducer orderEventProducer;

  /** 주문 상태 변경 이벤트를 커밋 이후 전송한다. */
  @SuppressWarnings("checkstyle:MissingJavadocMethod")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(OrderStatusChangedEvent event) {
    try {
      orderEventProducer.sendStatusChanged(event);
    } catch (Exception e) {
      log.warn("[OrderKafkaEventListener] 주문 상태 변경 이벤트 발행 실패 - orderId: {}, status: {}, cause: {}",
          event.getOrderId(), event.getOrderStatus(), e.getMessage());
    }
  }

  /** 주문 재고 복원 이벤트를 커밋 이후 전송한다. */
  @SuppressWarnings("checkstyle:MissingJavadocMethod")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(StockRestoreEvent event) {
    if (event.getOrderId() != null) {
      try {
        orderEventProducer.sendStockRestored(event);
      } catch (Exception e) {
        log.warn("[OrderKafkaEventListener] 주문 재고 복원 이벤트 발행 실패 - orderId: {}, cause: {}",
            event.getOrderId(), e.getMessage());
      }
    }
  }
}
