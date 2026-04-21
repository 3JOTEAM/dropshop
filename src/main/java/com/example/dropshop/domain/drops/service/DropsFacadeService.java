package com.example.dropshop.domain.drops.service;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.drops.dto.request.DropCreateRequest;
import com.example.dropshop.domain.drops.dto.request.DropUpdateRequest;
import com.example.dropshop.domain.drops.dto.response.DropResponse;
import com.example.dropshop.domain.drops.entity.Drops;
import com.example.dropshop.domain.drops.enums.DropsStatus;
import com.example.dropshop.domain.drops.exception.DropsException;
import com.example.dropshop.domain.order.facade.OrderFacadeService;
import com.example.dropshop.domain.product.entity.Product;
import com.example.dropshop.domain.product.enums.ProductStatus;
import com.example.dropshop.domain.product.service.ProductDomainFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 드랍 도메인 파사드 서비스.
 */
@Service
@RequiredArgsConstructor
public class DropsFacadeService {

  private final DropsService dropsService;
  private final ProductDomainFacadeService productDomainFacadeService;
  private final OrderFacadeService orderFacadeService;

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

    if (!drops.isScheduled() || orderFacadeService.existsOrderHistoryForDrop(dropId)) {
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
}

