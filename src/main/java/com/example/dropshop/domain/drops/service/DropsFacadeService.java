package com.example.dropshop.domain.drops.service;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.drops.dto.request.DropCreateRequest;
import com.example.dropshop.domain.drops.dto.request.DropUpdateRequest;
import com.example.dropshop.domain.drops.dto.response.DropResponse;
import com.example.dropshop.domain.drops.entity.Drops;
import com.example.dropshop.domain.drops.exception.DropsException;
import com.example.dropshop.domain.order.service.OrderHistoryQueryService;
import com.example.dropshop.domain.product.entity.Product;
import com.example.dropshop.domain.product.enums.ProductStatus;
import com.example.dropshop.domain.product.service.ProductDomainFacadeService;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 드랍 도메인 파사드 서비스.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DropsFacadeService {

  private final DropsService dropsService;
  private final ProductDomainFacadeService productDomainFacadeService;
  private final OrderFacadeService orderFacadeService;
  private final DropsRepository dropsRepository;
  private final OrderHistoryQueryService orderHistoryQueryService;

  /**
   * 판매자 드랍을 생성한다.
   */
  @Transactional
  public DropResponse createSellerDrop(
      Long sellerId,
      boolean sellerApproved,
      boolean sellerVerified,
      DropCreateRequest request
  ) {
    productDomainFacadeService.validateSellerState(sellerApproved, sellerVerified);

    Product product = productDomainFacadeService.findOwnedProduct(request.getProductId(), sellerId);
    validateDuplicatedOngoingDrop(product.getId());

    Drops saved = dropsService.create(product, request);
    productDomainFacadeService.updateStatusByDrop(product, ProductStatus.READY);
    return DropResponse.from(saved);
  }

  /**
   * 판매자 드랍을 수정한다.
   */
  @Transactional
  public DropResponse updateSellerDrop(
      Long dropId,
      Long sellerId,
      boolean sellerApproved,
      boolean sellerVerified,
      DropUpdateRequest request
  ) {
    productDomainFacadeService.validateSellerState(sellerApproved, sellerVerified);

    Drops drops = dropsService.findById(dropId);
    Product product = drops.getProduct();
    productDomainFacadeService.validateOwnership(product, sellerId);

    Drops saved = dropsService.update(drops, product.getStock(), request);
    return DropResponse.from(saved);
  }

  /**
   * 판매자 드랍을 삭제한다.
   */
  @Transactional
  public void deleteSellerDrop(
      Long dropId,
      Long sellerId,
      boolean sellerApproved,
      boolean sellerVerified
  ) {
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

  /**
   * 판매자 드랍을 강제 종료한다.
   */
  @Transactional
  public DropResponse stopSellerDrop(
      Long dropId,
      Long sellerId,
      boolean sellerApproved,
      boolean sellerVerified
  ) {
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

  /**
   * 상품 삭제를 막아야 하는 드랍 이력 존재 여부를 확인한다.
   */
  @Transactional(readOnly = true)
  public boolean existsDropHistoryForProduct(Long productId) {
    return dropsService.existsDropHistoryForProduct(productId);
  }

  private void validateDuplicatedOngoingDrop(Long productId) {
    if (dropsService.existsOngoingDropForProduct(productId)) {
      throw new DropsException(ErrorCode.DROP_ALREADY_EXISTS);
    }
  }

  /**
   * 특정 상품의 최신 드랍 1건을 조회한다.
   */
  @Transactional(readOnly = true)
  public Optional<Drops> findLatestDropByProductId(Long productId) {
    return dropsService.findLatestDropByProductId(productId);
  }

  /**
   * 상품별 최신 드랍 맵을 조회한다.
   */
  @Transactional(readOnly = true)
  public Map<Long, Drops> findLatestDropsByProductIds(Collection<Long> productIds) {
    return dropsService.findLatestDropsByProductIds(productIds);
  }

  /**
   * 주문 생성을 위해 드랍 재고를 차감한다.
   */
  @Transactional
  public Drops reserveStockForOrder(Long dropId, Long productId, int quantity) {
    Drops drops = dropsService.findById(dropId);
    validateOrderableDrop(drops, productId);

    drops.decrementRemainStock(quantity);
    if (drops.getRemainStock() == 0L) {
      drops.finish();
      productDomainFacadeService.updateStatusByDrop(drops.getProduct(), ProductStatus.OUT_OF_STOCK);
    }
    return drops;
  }

  /**
   * 주문 취소/결제 실패 시 드랍 재고를 복원한다.
   */
  @Transactional
  public void restoreStockForOrder(Long dropId, int quantity) {
    Drops drops = dropsService.findById(dropId);
    drops.restoreRemainStock(quantity);

    try {
      if (drops.isFinished()
          && drops.getRemainStock() > 0L
          && LocalDateTime.now().isBefore(drops.getEndAt())) {
        drops.activate();
        productDomainFacadeService.updateStatusByDrop(drops.getProduct(), ProductStatus.ON_SALE);
      }
    } catch (OptimisticLockingFailureException e) {
      log.info("드랍 ID={} 재활성화가 동시성 충돌로 스킵되었습니다.", dropId, e);
    }
  }

  private void validateOrderableDrop(Drops drops, Long productId) {
    if (!drops.isActive()) {
      throw new DropsException(ErrorCode.DROP_ORDER_NOT_ALLOWED);
    }
    if (!drops.getProduct().getId().equals(productId)) {
      throw new DropsException(ErrorCode.DROP_PRODUCT_MISMATCH);
    }
  }
}

