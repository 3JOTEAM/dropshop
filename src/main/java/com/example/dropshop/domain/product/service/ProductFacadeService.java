package com.example.dropshop.domain.product.service;

import com.example.dropshop.domain.notification.drops.service.DropsFacadeService;
import com.example.dropshop.domain.order.service.OrderItemFacadeService;
import com.example.dropshop.domain.product.dto.request.ProductCreateRequest;
import com.example.dropshop.domain.product.dto.request.ProductImageCreateRequest;
import com.example.dropshop.domain.product.dto.request.ProductImageUpdateRequest;
import com.example.dropshop.domain.product.dto.request.ProductStatusUpdateRequest;
import com.example.dropshop.domain.product.dto.request.ProductUpdateRequest;
import com.example.dropshop.domain.product.dto.response.ProductCreateResponse;
import com.example.dropshop.domain.product.dto.response.ProductDetailResponse;
import com.example.dropshop.domain.product.dto.response.ProductImageResponse;
import com.example.dropshop.domain.product.dto.response.ProductListItemResponse;
import com.example.dropshop.domain.product.dto.response.SellerProductListItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 파사드 서비스. */
@Service
@RequiredArgsConstructor
public class ProductFacadeService {

  private final ProductCommandService productCommandService;
  private final ProductQueryService productQueryService;
  private final DropsFacadeService dropsFacadeService;
  private final OrderItemFacadeService orderItemFacadeService;

  /** 판매자 상품을 생성한다. */
  @Transactional
  public ProductCreateResponse createSellerProduct(
      Long sellerId, boolean sellerApproved, boolean sellerVerified, ProductCreateRequest request) {
    return productCommandService.createSellerProduct(
        sellerId, sellerApproved, sellerVerified, request);
  }

  /** 판매자 상품 정보를 수정한다. */
  @Transactional
  public ProductCreateResponse updateSellerProduct(
      Long productId,
      Long sellerId,
      boolean sellerApproved,
      boolean sellerVerified,
      ProductUpdateRequest request) {
    return productCommandService.updateSellerProduct(
        productId, sellerId, sellerApproved, sellerVerified, request);
  }

  /** 판매자 상품 상태를 수동 변경한다. */
  @Transactional
  public ProductCreateResponse changeSellerProductStatus(
      Long productId,
      Long sellerId,
      boolean sellerApproved,
      boolean sellerVerified,
      ProductStatusUpdateRequest request) {
    return productCommandService.changeSellerProductStatus(
        productId, sellerId, sellerApproved, sellerVerified, request);
  }

  /** 판매자 상품을 삭제한다. */
  @Transactional
  public void deleteSellerProduct(
      Long productId, Long sellerId, boolean sellerApproved, boolean sellerVerified) {
    boolean hasDropHistory = dropsFacadeService.existsDropHistoryForProduct(productId);
    boolean hasOrderHistory = orderItemFacadeService.existsOrderHistoryForProduct(productId);

    productCommandService.deleteSellerProduct(
        productId, sellerId, sellerApproved, sellerVerified, hasDropHistory, hasOrderHistory);
  }

  /** 판매자 상품 이미지를 추가한다. */
  @Transactional
  public ProductImageResponse createSellerProductImage(
      Long productId,
      Long sellerId,
      boolean sellerApproved,
      boolean sellerVerified,
      ProductImageCreateRequest request) {
    return productCommandService.createSellerProductImage(
        productId, sellerId, sellerApproved, sellerVerified, request);
  }

  /** 판매자 상품 이미지를 수정한다. */
  @Transactional
  public ProductImageResponse updateSellerProductImage(
      Long productId,
      Long imageId,
      Long sellerId,
      boolean sellerApproved,
      boolean sellerVerified,
      ProductImageUpdateRequest request) {
    return productCommandService.updateSellerProductImage(
        productId, imageId, sellerId, sellerApproved, sellerVerified, request);
  }

  /** 판매자 상품 이미지를 삭제한다. */
  @Transactional
  public void deleteSellerProductImage(
      Long productId, Long imageId, Long sellerId, boolean sellerApproved, boolean sellerVerified) {
    productCommandService.deleteSellerProductImage(
        productId, imageId, sellerId, sellerApproved, sellerVerified);
  }

  /** 공개 상품 목록을 조회한다. */
  @Transactional(readOnly = true)
  public Page<ProductListItemResponse> findPublicProducts(
      String status, String sort, Pageable pageable) {
    return productQueryService.findPublicProducts(status, sort, pageable);
  }

  /** 공개 상품 상세를 조회한다. */
  @Transactional(readOnly = true)
  public ProductDetailResponse findPublicProductDetail(Long productId) {
    return productQueryService.findPublicProductDetail(productId);
  }

  /** 판매자 본인 상품 목록을 조회한다. */
  @Transactional(readOnly = true)
  public Page<SellerProductListItemResponse> findSellerProducts(Long sellerId, Pageable pageable) {
    return productQueryService.findSellerProducts(sellerId, pageable);
  }
}
