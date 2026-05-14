package com.example.dropshop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.service.DropsFacadeService;
import com.example.dropshop.domain.product.config.ProductConstraints;
import com.example.dropshop.domain.product.dto.response.ProductDetailResponse;
import com.example.dropshop.domain.product.dto.response.ProductListItemResponse;
import com.example.dropshop.domain.product.dto.response.SellerProductListItemResponse;
import com.example.dropshop.domain.product.entity.Product;
import com.example.dropshop.domain.product.enums.ProductStatus;
import com.example.dropshop.domain.product.exception.ProductException;
import com.example.dropshop.domain.product.repository.ProductRepository;
import com.example.dropshop.domain.user.entity.User;
import com.example.dropshop.domain.user.service.UserFacadeService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductQueryServiceTest {

  @Mock private ProductRepository productRepository;

  @Mock private DropsFacadeService dropsFacadeService;

  @Mock private UserFacadeService userFacadeService;

  private ProductQueryService productQueryService;

  @BeforeEach
  void setUp() {
    ProductConstraints constraints = new ProductConstraints();
    constraints.setMaxImageCount(5);
    constraints.setPurchasableBlockHours(24);
    productQueryService =
        new ProductQueryService(
            productRepository, dropsFacadeService, userFacadeService, constraints);
  }

  @Test
  @DisplayName("공개 상품 목록 기본 조회 성공")
  void findPublicProducts_defaultLatest_success() {
    Product product = createProduct(11L, ProductStatus.READY);
    LocalDateTime dropStartAt = LocalDateTime.now().plusDays(2);
    Drops latestDrop = createDrop(product, dropStartAt);
    Page<Product> page = new PageImpl<Product>(List.of(product), PageRequest.of(0, 10), 1);

    given(
            productRepository.findPublicProducts(
                anyCollection(), any(), any(LocalDateTime.class), any(Pageable.class)))
        .willReturn(page);
    given(dropsFacadeService.findLatestDropsByProductIds(List.of(11L)))
        .willReturn(Map.of(11L, latestDrop));

    Page<ProductListItemResponse> result =
        productQueryService.findPublicProducts(null, null, PageRequest.of(0, 10));

    assertThat(result.getContent()).hasSize(1);
    ProductListItemResponse item = result.getContent().get(0);
    assertThat(item.getProductId()).isEqualTo(11L);
    assertThat(item.getStatus()).isEqualTo(ProductStatus.READY.name());
    assertThat(item.getDropStartAt()).isEqualTo(dropStartAt);
  }

  @Test
  @DisplayName("공개 상품 목록 조회 시 status 값이 잘못되면 예외를 던진다")
  void findPublicProducts_invalidStatus_throwsException() {
    assertThatThrownBy(
            () ->
                productQueryService.findPublicProducts("WRONG_STATUS", null, PageRequest.of(0, 10)))
        .isInstanceOf(ProductException.class)
        .hasMessageContaining("status는 READY, ON_SALE, OUT_OF_STOCK");

    verifyNoInteractions(productRepository, dropsFacadeService);
  }

  @Test
  @DisplayName("공개 상품 목록에서 HIDDEN 상태는 조회할 수 없다")
  void findPublicProducts_hiddenStatus_throwsException() {
    assertThatThrownBy(
            () -> productQueryService.findPublicProducts("HIDDEN", null, PageRequest.of(0, 10)))
        .isInstanceOf(ProductException.class)
        .hasMessageContaining("공개 목록에서는 READY, ON_SALE, OUT_OF_STOCK만 조회");

    verifyNoInteractions(productRepository, dropsFacadeService);
  }

  @Test
  @DisplayName("드랍 임박 정렬은 전용 쿼리를 호출한다")
  void findPublicProducts_dropImminent_callsCustomQuery() {
    Page<Product> page = new PageImpl<Product>(List.of(), PageRequest.of(0, 10), 0);

    given(
            productRepository.findPublicProducts(
                anyCollection(), any(), any(LocalDateTime.class), any(Pageable.class)))
        .willReturn(page);
    given(dropsFacadeService.findLatestDropsByProductIds(List.of())).willReturn(Map.of());

    productQueryService.findPublicProducts("READY", "DROP_IMMINENT", PageRequest.of(0, 10));

    verify(productRepository)
        .findPublicProducts(anyCollection(), any(), any(LocalDateTime.class), any(Pageable.class));
    verify(productRepository, never()).findAllByStatusIn(anyCollection(), any(Pageable.class));
  }

  @Test
  @DisplayName("공개 상품 상세 조회 시 HIDDEN 상태는 조회할 수 없다")
  void findPublicProductDetail_hiddenProduct_throwsException() {
    Product hiddenProduct = createProduct(21L, ProductStatus.HIDDEN);
    given(productRepository.findDetailById(21L)).willReturn(Optional.of(hiddenProduct));

    assertThatThrownBy(() -> productQueryService.findPublicProductDetail(21L))
        .isInstanceOf(ProductException.class)
        .hasMessage(ErrorCode.PRODUCT_NOT_FOUND.getMessage());
  }

  @Test
  @DisplayName("공개 상품 상세 조회 시 구매 가능 여부를 계산한다")
  void findPublicProductDetail_calculatesPurchasable() {
    Product product = createProduct(31L, ProductStatus.READY);
    // product 내부 sellerId = 1L 이므로 User.id도 1L로 맞춤
    Drops latestDrop = createDrop(product, LocalDateTime.now().plusHours(48));
    User seller = User.signup("seller@test.com", "encoded-password", "판매자");
    ReflectionTestUtils.setField(seller, "id", 1L); // 31L → 1L 수정

    given(productRepository.findDetailById(31L)).willReturn(Optional.of(product));
    given(dropsFacadeService.findLatestDropByProductId(31L)).willReturn(Optional.of(latestDrop));
    given(userFacadeService.findById(1L)).willReturn(Optional.of(seller));

    ProductDetailResponse response = productQueryService.findPublicProductDetail(31L);

    assertThat(response.isPurchasable()).isTrue();
    assertThat(response.getSeller().getSellerId()).isEqualTo(1L); // 31L → 1L 수정
    assertThat(response.getLatestDrop().getDropId()).isEqualTo(latestDrop.getId());
  }

  @Test
  @DisplayName("판매자 상품 목록 조회는 createdAt 내림차순 정렬을 강제한다")
  void findSellerProducts_appliesCreatedAtDescSort() {
    Product product = createProduct(41L, ProductStatus.ON_SALE);
    Page<Product> page = new PageImpl<Product>(List.of(product), PageRequest.of(0, 10), 1);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

    given(productRepository.findAllBySellerId(anyLong(), any(Pageable.class))).willReturn(page);

    Page<SellerProductListItemResponse> result =
        productQueryService.findSellerProducts(1L, PageRequest.of(0, 10));

    verify(productRepository).findAllBySellerId(anyLong(), pageableCaptor.capture());
    Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
    assertThat(order).isNotNull();
    assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getProductId()).isEqualTo(41L);
  }

  private Product createProduct(Long id, ProductStatus status) {
    Product product =
        Product.create(
            1L,
            "테스트 상품",
            "SHOES",
            BigDecimal.valueOf(100000),
            10,
            20,
            "https://cdn.example.com/thumb.jpg",
            "설명",
            "스펙",
            "배송 정책",
            "환불 정책");
    ReflectionTestUtils.setField(product, "id", id);
    product.updateStatusByDrop(status);
    return product;
  }

  private Drops createDrop(Product product, LocalDateTime startAt) {
    Drops drops = Drops.create(product, startAt, startAt.plusDays(1), 30L, 1L, true);
    ReflectionTestUtils.setField(drops, "id", 900L);
    return drops;
  }
}
