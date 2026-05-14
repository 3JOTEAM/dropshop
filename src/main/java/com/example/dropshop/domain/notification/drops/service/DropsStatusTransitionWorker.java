package com.example.dropshop.domain.notification.drops.service;

import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import com.example.dropshop.domain.notification.drops.event.DropStatusChangedEvent;
import com.example.dropshop.domain.notification.drops.producer.DropsStatusChangedEventProducer;
import com.example.dropshop.domain.product.enums.ProductStatus;
import com.example.dropshop.domain.product.service.ProductDomainFacadeService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 드랍 상태 전이 단건 처리 워커. */
@Service
@RequiredArgsConstructor
public class DropsStatusTransitionWorker {

  private final DropsService dropsService;
  private final ProductDomainFacadeService productDomainFacadeService;
  private final DropsStockPreemptionService dropsStockPreemptionService;
  private final DropsStatusChangedEventProducer dropsStatusChangedEventProducer;

  /** 단일 예정 드랍을 ACTIVE로 전이한다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public boolean transitionScheduledDrop(Long dropId) {
    Drops drops = dropsService.findById(dropId);
    if (!drops.isScheduled()) {
      return false;
    }

    final DropsStatus fromStatus = drops.getStatus();
    drops.activate();
    productDomainFacadeService.updateStatusByDrop(drops.getProduct(), ProductStatus.ON_SALE);
    registerAfterCommit(() -> dropsStockPreemptionService.preloadStockKey(dropId));
    publishStatusChangedEvent(drops, fromStatus, DropsStatus.ACTIVE);
    return true;
  }

  /** 단일 진행 중 드랍을 FINISHED로 전이한다. */
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public boolean transitionActiveDrop(Long dropId) {
    Drops drops = dropsService.findById(dropId);
    if (!drops.isActive()) {
      return false;
    }

    final DropsStatus fromStatus = drops.getStatus();
    drops.finish();
    productDomainFacadeService.updateStatusByDrop(drops.getProduct(), ProductStatus.OUT_OF_STOCK);
    publishStatusChangedEvent(drops, fromStatus, DropsStatus.FINISHED);
    return true;
  }

  private void registerAfterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
      return;
    }

    action.run();
  }

  private void publishStatusChangedEvent(
      Drops drops, DropsStatus fromStatus, DropsStatus toStatus) {
    DropStatusChangedEvent event =
        DropStatusChangedEvent.builder()
            .dropId(drops.getId())
            .productId(drops.getProduct().getId())
            .fromStatus(fromStatus)
            .toStatus(toStatus)
            .cause("SCHEDULER")
            .occurredAt(LocalDateTime.now())
            .build();

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              dropsStatusChangedEventProducer.send(event);
            }
          });
      return;
    }

    dropsStatusChangedEventProducer.send(event);
  }
}
