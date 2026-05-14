package com.example.dropshop.domain.product.service;

import com.example.dropshop.common.config.CacheNames;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.service.DropsFacadeService;
import com.example.dropshop.domain.product.config.ProductConstraints;
import com.example.dropshop.domain.product.dto.response.ProductDetailResponse;
import com.example.dropshop.domain.product.dto.response.ProductListItemResponse;
import com.example.dropshop.domain.product.dto.response.SellerProductListItemResponse;
import com.example.dropshop.domain.product.entity.Product;
import com.example.dropshop.domain.product.enums.ProductListSortType;
import com.example.dropshop.domain.product.enums.ProductStatus;
import com.example.dropshop.domain.product.exception.ProductException;
import com.example.dropshop.domain.product.repository.ProductRepository;
import com.example.dropshop.domain.user.entity.User;
import com.example.dropshop.domain.user.service.UserFacadeService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 상품 읽기 도메인 서비스. */
@Service
@RequiredArgsConstructor
public class ProductQueryService {

  private static final Collection<ProductStatus> PUBLIC_PRODUCT_STATUSES =
      EnumSet.of(ProductStatus.READY, ProductStatus.ON_SALE, ProductStatus.OUT_OF_STOCK);

  private final ProductRepository productRepository;
  private final DropsFacadeService dropsFacadeService;
  private final UserFacadeService userFacadeService;
  private final ProductConstraints constraints;

  /**
   * 공개 상품 목록을 조회한다.
   *
   * <p>동일 파라미터(status, sort, page, size)로 반복 요청 시 Redis 캐시에서 반환한다. TTL: 30초 / 상품·드랍 변경 시 {@code
   * product:list} 캐시 전체 무효화.
   */
  @Cacheable(
      value = CacheNames.PRODUCT_LIST,
      key =
          "'status=' + #status + ':sort=' + #sort + ':page=' + #pageable.pageNumber + ':size=' + #pageable.pageSize")
  @Transactional(readOnly = true)
  public Page<ProductListItemResponse> findPublicProducts(
      String status, String sort, Pageable pageable) {
    Collection<ProductStatus> filterStatuses = parsePublicStatusFilter(status);
    ProductListSortType sortType = parseSortType(sort);

    Page<Product> products = findPublicProductsBySort(filterStatuses, sortType, pageable);
    Map<Long, Drops> latestDrops =
        dropsFacadeService.findLatestDropsByProductIds(
            products.stream().map(Product::getId).toList());

    List<ProductListItemResponse> content =
        products.getContent().stream()
            .map(product -> ProductListItemResponse.of(product, latestDrops.get(product.getId())))
            .toList();

    return new PageImpl<>(content, products.getPageable(), products.getTotalElements());
  }

  /**
   * 공개 상품 상세를 조회한다.
   *
   * <p>상품 ID 기준으로 Redis 캐시에서 반환한다. TTL: 60초 / 상품 수정·이미지 변경·드랍 변경 시 해당 상품 캐시 무효화.
   */
  @Cacheable(value = CacheNames.PRODUCT_DETAIL, key = "#productId")
  @Transactional(readOnly = true)
  public ProductDetailResponse findPublicProductDetail(Long productId) {
    Product product =
        productRepository
            .findDetailById(productId)
            .orElseThrow(() -> new ProductException(ErrorCode.PRODUCT_NOT_FOUND));

    if (product.getStatus() == ProductStatus.HIDDEN) {
      throw new ProductException(ErrorCode.PRODUCT_NOT_FOUND);
    }

    Drops latestDrop = dropsFacadeService.findLatestDropByProductId(productId).orElse(null);
    User seller = userFacadeService.findById(product.getSellerId()).orElse(null);
    boolean isPurchasable = calculatePurchasable(latestDrop);

    return ProductDetailResponse.of(product, latestDrop, seller, isPurchasable);
  }

  /** 판매자 본인 상품 목록을 조회한다. */
  @Transactional(readOnly = true)
  public Page<SellerProductListItemResponse> findSellerProducts(Long sellerId, Pageable pageable) {
    Page<Product> products =
        productRepository.findAllBySellerId(
            sellerId, applySort(pageable, Sort.by(Sort.Direction.DESC, "createdAt")));
    return products.map(SellerProductListItemResponse::from);
  }

  private Page<Product> findPublicProductsBySort(
      Collection<ProductStatus> statuses, ProductListSortType sortType, Pageable pageable) {
    Pageable normalizedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    return productRepository.findPublicProducts(
        statuses, sortType, LocalDateTime.now(), normalizedPageable);
  }

  private Collection<ProductStatus> parsePublicStatusFilter(String status) {
    if (status == null || status.isBlank()) {
      return PUBLIC_PRODUCT_STATUSES;
    }

    ProductStatus parsedStatus;
    try {
      parsedStatus = ProductStatus.valueOf(status.toUpperCase());
    } catch (IllegalArgumentException exception) {
      throw new ProductException(
          ErrorCode.VALIDATION_ERROR, "status는 READY, ON_SALE, OUT_OF_STOCK 중 하나여야 합니다.");
    }

    if (!PUBLIC_PRODUCT_STATUSES.contains(parsedStatus)) {
      throw new ProductException(
          ErrorCode.VALIDATION_ERROR, "공개 목록에서는 READY, ON_SALE, OUT_OF_STOCK만 조회할 수 있습니다.");
    }
    return EnumSet.of(parsedStatus);
  }

  private ProductListSortType parseSortType(String sort) {
    if (sort == null || sort.isBlank()) {
      return ProductListSortType.LATEST;
    }
    try {
      return ProductListSortType.valueOf(sort.toUpperCase());
    } catch (IllegalArgumentException exception) {
      throw new ProductException(
          ErrorCode.VALIDATION_ERROR,
          "sort는 LATEST, PRICE_HIGH, PRICE_LOW, DROP_IMMINENT 중 하나여야 합니다.");
    }
  }

  private boolean calculatePurchasable(Drops latestDrop) {
    if (latestDrop == null) {
      return false;
    }

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime startAt = latestDrop.getStartAt();
    if (startAt == null) {
      return false;
    }

    long hoursUntilStart = Duration.between(now, startAt).toHours();
    return hoursUntilStart >= constraints.getPurchasableBlockHours();
  }

  private Pageable applySort(Pageable pageable, Sort sort) {
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
  }
}
