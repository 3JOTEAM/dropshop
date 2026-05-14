package com.example.dropshop.domain.notification.drops.service;

import com.example.dropshop.common.config.CacheNames;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.lock.LockKeys;
import com.example.dropshop.common.lock.RedisLockService;
import com.example.dropshop.domain.notification.drops.dto.request.DropCreateRequest;
import com.example.dropshop.domain.notification.drops.dto.request.DropUpdateRequest;
import com.example.dropshop.domain.notification.drops.dto.response.DropResponse;
import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.exception.DropsException;
import com.example.dropshop.domain.notification.drops.repository.DropsRepository;
import com.example.dropshop.domain.order.service.OrderHistoryQueryService;
import com.example.dropshop.domain.product.entity.Product;
import com.example.dropshop.domain.product.enums.ProductStatus;
import com.example.dropshop.domain.product.service.ProductDomainFacadeService;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 드랍 도메인 파사드 서비스. */
@Service
@Slf4j
@RequiredArgsConstructor
public class DropsFacadeService {

  private final DropsService dropsService;
  private final ProductDomainFacadeService productDomainFacadeService;
  private final OrderHistoryQueryService orderHistoryQueryService;
  private final DropsRepository dropsRepository;
  private final RedisLockService redisLockService;
  private final DropsStockPreemptionService dropsStockPreemptionService;

  /** 판매자 드랍을 생성한다. */
  @Caching(
      evict = {
        @CacheEvict(value = CacheNames.PRODUCT_LIST, allEntries = true),
        @CacheEvict(value = CacheNames.PRODUCT_DETAIL, key = "#request.productId"),
        @CacheEvict(value = CacheNames.DROP_LATEST, key = "#request.productId")
      })
  @Transactional
  public DropResponse createSellerDrop(
      Long sellerId, boolean sellerApproved, boolean sellerVerified, DropCreateRequest request) {
    productDomainFacadeService.validateSellerState(sellerApproved, sellerVerified);

    Product product = productDomainFacadeService.findOwnedProduct(request.getProductId(), sellerId);
    validateDuplicatedOngoingDrop(product.getId());

    Drops saved = dropsService.create(product, request);
    productDomainFacadeService.updateStatusByDrop(product, ProductStatus.READY);
    return DropResponse.from(saved);
  }

  /** 판매자 드랍을 수정한다. */
  @Caching(
      evict = {
        @CacheEvict(value = CacheNames.PRODUCT_LIST, allEntries = true),
        @CacheEvict(value = CacheNames.PRODUCT_DETAIL, allEntries = true),
        @CacheEvict(value = CacheNames.DROP_LATEST, allEntries = true)
      })
  @Transactional
  public DropResponse updateSellerDrop(
      Long dropId,
      Long sellerId,
      boolean sellerApproved,
      boolean sellerVerified,
      DropUpdateRequest request) {
    productDomainFacadeService.validateSellerState(sellerApproved, sellerVerified);

    Drops drops = dropsService.findById(dropId);
    Product product = drops.getProduct();
    productDomainFacadeService.validateOwnership(product, sellerId);

    Drops saved = dropsService.update(drops, product.getStock(), request);
    return DropResponse.from(saved);
  }

  /** 판매자 드랍을 삭제한다. */
  @Caching(
      evict = {
        @CacheEvict(value = CacheNames.PRODUCT_LIST, allEntries = true),
        @CacheEvict(value = CacheNames.PRODUCT_DETAIL, allEntries = true),
        @CacheEvict(value = CacheNames.DROP_LATEST, allEntries = true)
      })
  @Transactional
  public void deleteSellerDrop(
      Long dropId, Long sellerId, boolean sellerApproved, boolean sellerVerified) {
    productDomainFacadeService.validateSellerState(sellerApproved, sellerVerified);

    Drops drops = dropsService.findById(dropId);
    Product product = drops.getProduct();
    productDomainFacadeService.validateOwnership(product, sellerId);

    if (!drops.isScheduled() || orderHistoryQueryService.existsOrderHistoryForDrop(dropId)) {
      throw new DropsException(ErrorCode.DROP_DELETE_NOT_ALLOWED);
    }

    dropsService.delete(drops);

    if (!dropsService.existsOngoingDropForProduct(product.getId())) {
      productDomainFacadeService.updateStatusByDrop(product, ProductStatus.HIDDEN);
    }
  }

  /** 판매자 드랍을 강제 종료한다. */
  @Caching(
      evict = {
        @CacheEvict(value = CacheNames.PRODUCT_LIST, allEntries = true),
        @CacheEvict(value = CacheNames.PRODUCT_DETAIL, allEntries = true),
        @CacheEvict(value = CacheNames.DROP_LATEST, allEntries = true)
      })
  @Transactional
  public DropResponse stopSellerDrop(
      Long dropId, Long sellerId, boolean sellerApproved, boolean sellerVerified) {
    productDomainFacadeService.validateSellerState(sellerApproved, sellerVerified);

    Drops drops = dropsService.findById(dropId);
    Product product = drops.getProduct();
    productDomainFacadeService.validateOwnership(product, sellerId);

    if (drops.isFinished()) {
      throw new DropsException(ErrorCode.DROP_STOP_NOT_ALLOWED);
    }

    drops.finish();
    productDomainFacadeService.updateStatusByDrop(product, ProductStatus.OUT_OF_STOCK);
    return DropResponse.from(drops);
  }

  /** 상품 삭제를 막아야 하는 드랍 이력 존재 여부를 확인한다. */
  @Transactional(readOnly = true)
  public boolean existsDropHistoryForProduct(Long productId) {
    return dropsService.existsDropHistoryForProduct(productId);
  }

  private void validateDuplicatedOngoingDrop(Long productId) {
    if (dropsService.existsOngoingDropForProduct(productId)) {
      throw new DropsException(ErrorCode.DROP_ALREADY_EXISTS);
    }
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
      Long productId = drops.getProduct().getId();
      latestDrops.putIfAbsent(productId, drops);
    }
    return latestDrops;
  }

  /**
   * 주문 취소/결제 실패 시 드랍 재고를 복원한다.
   *
   * <p>분산락으로 동시 복원 요청을 직렬화해 {@code @Version} 기반 낙관적 락 충돌을 최소화한다.
   */
  @Caching(
      evict = {
        @CacheEvict(value = CacheNames.PRODUCT_LIST, allEntries = true),
        @CacheEvict(value = CacheNames.PRODUCT_DETAIL, allEntries = true),
        @CacheEvict(value = CacheNames.DROP_LATEST, allEntries = true)
      })
  @Transactional
  public void restoreStockForOrder(Long dropId, int quantity) {
    redisLockService.executeWithLock(
        LockKeys.dropStock(dropId),
        () -> {
          Drops drops = dropsService.findById(dropId);
          drops.restoreRemainStock(quantity);

          registerAfterCommit(
              () -> dropsStockPreemptionService.increaseStockAfterRestore(dropId, quantity));

          try {
            if (drops.isFinished()
                && drops.getRemainStock() > 0L
                && LocalDateTime.now().isBefore(drops.getEndAt())) {
              drops.activate();
              productDomainFacadeService.updateStatusByDrop(
                  drops.getProduct(), ProductStatus.ON_SALE);
            }
          } catch (OptimisticLockingFailureException e) {
            log.info("드랍 ID={} 재활성화가 동시성 충돌로 스킵되었습니다.", dropId, e);
          }
          return null;
        });
  }

  /**
   * 주문 생성을 위해 드랍 재고를 선점(차감)한다.
   *
   * @param dropId 드랍 ID
   * @param productId 상품 ID
   * @param quantity 구매 수량
   * @return 재고 차감이 반영된 드랍
   */
  @Transactional
  public Drops reserveStockForOrder(Long dropId, Long productId, int quantity) {
    if (quantity <= 0) {
      throw new DropsException(ErrorCode.INVALID_DROP_ORDER_QUANTITY);
    }

    boolean reserved = dropsStockPreemptionService.tryReserve(dropId, quantity);
    if (!reserved) {
      throw new DropsException(ErrorCode.INVALID_DROP_REMAIN_STOCK);
    }

    // Redis 선점 성공 이후에는 예외 발생 여부와 무관하게 롤백 보상을 등록한다.
    registerRollbackCompensation(dropId, quantity);

    return redisLockService.executeWithLock(
        LockKeys.dropStock(dropId),
        () -> {
          Drops found = dropsService.findById(dropId);

          if (!found.isActive()) {
            throw new DropsException(ErrorCode.DROP_ORDER_NOT_ALLOWED);
          }
          if (!found.getProduct().getId().equals(productId)) {
            throw new DropsException(ErrorCode.DROP_PRODUCT_MISMATCH);
          }

          found.decrementRemainStock(quantity);
          if (found.getRemainStock() <= 0L) {
            found.finish();
            productDomainFacadeService.updateStatusByDrop(
                found.getProduct(), ProductStatus.OUT_OF_STOCK);
          }
          return found;
        });
  }

  private void registerRollbackCompensation(Long dropId, int quantity) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
              dropsStockPreemptionService.compensateReservedStock(dropId, quantity);
            }
          }
        });
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
}
