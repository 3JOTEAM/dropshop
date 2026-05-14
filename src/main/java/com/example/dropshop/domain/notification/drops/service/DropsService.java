package com.example.dropshop.domain.notification.drops.service;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.notification.drops.dto.request.DropCreateRequest;
import com.example.dropshop.domain.notification.drops.dto.request.DropUpdateRequest;
import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import com.example.dropshop.domain.notification.drops.exception.DropsException;
import com.example.dropshop.domain.notification.drops.repository.DropsRepository;
import com.example.dropshop.domain.product.entity.Product;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 드랍 도메인 서비스. */
@Service
@RequiredArgsConstructor
public class DropsService {

  private final DropsRepository dropsRepository;

  /** 드랍을 생성한다. */
  @Transactional
  public Drops create(Product product, DropCreateRequest request) {
    validateStartAt(request.getStartAt());
    validateTotalStockWithinProductStock(request.getTotalStock(), product.getStock());

    Drops drops =
        Drops.create(
            product,
            request.getStartAt(),
            request.getEndAt(),
            request.getTotalStock(),
            request.getPurchaseLimit(),
            Boolean.TRUE.equals(request.getUseQueue()));

    return dropsRepository.save(drops);
  }

  /** 드랍을 수정한다. */
  @Transactional
  public Drops update(Drops drops, int productStock, DropUpdateRequest request) {
    if (drops.isFinished()) {
      throw new DropsException(ErrorCode.DROP_UPDATE_NOT_ALLOWED);
    }
    if (drops.isActive() && (request.getStartAt() != null || request.getTotalStock() != null)) {
      throw new DropsException(ErrorCode.DROP_ACTIVE_UPDATE_LOCKED);
    }

    final LocalDateTime updatedStartAt =
        request.getStartAt() == null ? drops.getStartAt() : request.getStartAt();
    final LocalDateTime updatedEndAt =
        request.getEndAt() == null ? drops.getEndAt() : request.getEndAt();
    final Long updatedTotalStock =
        request.getTotalStock() == null ? drops.getTotalStock() : request.getTotalStock();
    final Long updatedPurchaseLimit =
        request.getPurchaseLimit() == null ? drops.getPurchaseLimit() : request.getPurchaseLimit();
    final boolean updatedUseQueue =
        request.getUseQueue() == null ? drops.isUseQueue() : request.getUseQueue();

    if (drops.isScheduled()) {
      validateStartAt(updatedStartAt);
    }
    validateTotalStockWithinProductStock(updatedTotalStock, productStock);

    long soldCount = drops.getTotalStock() - drops.getRemainStock();
    long updatedRemainStock = updatedTotalStock - soldCount;
    if (updatedRemainStock < 0L) {
      throw new DropsException(ErrorCode.INVALID_DROP_REMAIN_STOCK);
    }

    drops.update(
        updatedStartAt,
        updatedEndAt,
        updatedTotalStock,
        updatedRemainStock,
        updatedPurchaseLimit,
        updatedUseQueue);
    return dropsRepository.save(drops);
  }

  /** 드랍을 조회한다. */
  @Transactional(readOnly = true)
  public Drops findById(Long dropId) {
    return dropsRepository
        .findById(dropId)
        .orElseThrow(() -> new DropsException(ErrorCode.DROP_NOT_FOUND));
  }

  /** 진행 중이거나 예정된 드랍 존재 여부를 확인한다. */
  @Transactional(readOnly = true)
  public boolean existsOngoingDropForProduct(Long productId) {
    return dropsRepository.existsByProductIdAndStatusIn(
        productId, DropsConstants.ONGOING_DROP_STATUSES);
  }

  /** 상품 삭제를 막아야 하는 드랍 이력 존재 여부를 확인한다. */
  @Transactional(readOnly = true)
  public boolean existsDropHistoryForProduct(Long productId) {
    return dropsRepository.existsByProductIdAndStatusIn(
        productId, DropsConstants.NON_DELETABLE_DROP_STATUSES);
  }

  /** 시작 시간이 도달한 예정 드랍 목록을 조회한다. */
  @Transactional(readOnly = true)
  public List<Drops> findScheduledDropsToActivate(LocalDateTime baseTime) {
    return dropsRepository.findAllByStatusAndStartAtLessThanEqual(DropsStatus.SCHEDULED, baseTime);
  }

  /** 종료되어야 하는 진행 중 드랍 목록을 조회한다. */
  @Transactional(readOnly = true)
  public List<Drops> findActiveDropsToFinish(LocalDateTime baseTime) {
    return dropsRepository.findAllActiveDropsToFinish(DropsStatus.ACTIVE, baseTime);
  }

  /** 특정 상품의 최신 드랍 1건을 조회한다. */
  @Transactional(readOnly = true)
  public Optional<Drops> findLatestDropByProductId(Long productId) {
    return dropsRepository.findTopByProductIdOrderByStartAtDesc(productId);
  }

  /** 상품별 최신 드랍 맵을 조회한다. */
  @Transactional(readOnly = true)
  public Map<Long, Drops> findLatestDropsByProductIds(Collection<Long> productIds) {
    List<Drops> dropsList =
        dropsRepository.findAllByProductIdInOrderByProductIdAscStartAtDesc(productIds);
    Map<Long, Drops> latestDrops = new HashMap<>();
    for (Drops drops : dropsList) {
      latestDrops.putIfAbsent(drops.getProduct().getId(), drops);
    }
    return latestDrops;
  }

  /** 드랍을 삭제한다. */
  @Transactional
  public void delete(Drops drops) {
    dropsRepository.delete(drops);
  }

  private void validateStartAt(LocalDateTime startAt) {
    if (startAt == null || !startAt.isAfter(LocalDateTime.now())) {
      throw new DropsException(ErrorCode.INVALID_DROP_START_AT);
    }
  }

  private void validateTotalStockWithinProductStock(Long totalStock, int productStock) {
    if (totalStock != null && totalStock > productStock) {
      throw new DropsException(ErrorCode.DROP_TOTAL_STOCK_EXCEEDS_PRODUCT_STOCK);
    }
  }
}
