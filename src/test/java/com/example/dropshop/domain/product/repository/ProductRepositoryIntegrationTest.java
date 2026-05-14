package com.example.dropshop.domain.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dropshop.common.config.QuerydslConfig;
import com.example.dropshop.domain.notification.drops.repository.DropsRepository;
import com.example.dropshop.domain.product.entity.Product;
import com.example.dropshop.domain.product.entity.ProductImage;
import com.example.dropshop.domain.product.enums.ProductStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@DataJpaTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:product-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.sql.init.mode=never"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuerydslConfig.class)
class ProductRepositoryIntegrationTest {

  @Autowired private ProductRepository productRepository;

  @Autowired private DropsRepository dropsRepository;

  @Autowired private EntityManager entityManager;

  @Test
  @DisplayName("status 필터 + salePrice 정렬로 공개 상품 조회가 된다")
  void findAllByStatusIn_filtersAndSortsBySalePrice() {
    Product hidden = saveProduct(1L, "hidden", BigDecimal.valueOf(120000), 0, ProductStatus.HIDDEN);
    Product ready = saveProduct(1L, "ready", BigDecimal.valueOf(100000), 0, ProductStatus.READY);
    Product onSale =
        saveProduct(1L, "onSale", BigDecimal.valueOf(100000), 10, ProductStatus.ON_SALE);

    Page<Product> page =
        productRepository.findAllByStatusIn(
            EnumSet.of(ProductStatus.READY, ProductStatus.ON_SALE),
            PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "salePrice")));

    assertThat(page.getContent())
        .extracting(Product::getId)
        .containsExactly(onSale.getId(), ready.getId());
    assertThat(page.getContent()).extracting(Product::getId).doesNotContain(hidden.getId());
  }

  @Test
  @DisplayName("상세 조회는 이미지 컬렉션을 함께 로딩한다")
  void findDetailById_loadsImagesWithEntityGraph() {
    Product product = saveProduct(2L, "detail", BigDecimal.valueOf(99000), 5, ProductStatus.READY);

    product.addImage(
        ProductImage.builder()
            .product(product)
            .imageUrl("https://cdn.example.com/detail-1.jpg")
            .sortOrder(1)
            .isThumbnail(true)
            .build());
    product.addImage(
        ProductImage.builder()
            .product(product)
            .imageUrl("https://cdn.example.com/detail-2.jpg")
            .sortOrder(2)
            .isThumbnail(false)
            .build());
    productRepository.saveAndFlush(product);

    entityManager.clear();

    Product loaded = productRepository.findDetailById(product.getId()).orElseThrow();

    // @EntityGraph가 실제로 eager loading 했는지 검증
    jakarta.persistence.PersistenceUnitUtil persistenceUnitUtil =
        entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
    assertThat(persistenceUnitUtil.isLoaded(loaded, "images")).isTrue();

    assertThat(loaded.getImages()).hasSize(2);
    assertThat(loaded.getImages())
        .extracting(ProductImage::getImageUrl)
        .containsExactly(
            "https://cdn.example.com/detail-1.jpg", "https://cdn.example.com/detail-2.jpg");
  }

  //  @Test
  //  @DisplayName("드랍 시작 임박 순 정렬 쿼리가 시작 시간이 빠른 상품을 우선 반환한다")
  //  void findPublicProductsOrderByDropImminent_ordersByNearestDropStartAt() {
  //    LocalDateTime baseTime = LocalDateTime.now();
  //
  //    Product laterProduct = saveProduct(3L, "later", BigDecimal.valueOf(110000), 0,
  // ProductStatus.READY);
  //    Product soonerProduct = saveProduct(4L, "sooner", BigDecimal.valueOf(115000), 0,
  // ProductStatus.READY);
  //
  //    dropsRepository.save(Drops.create(
  //        laterProduct,
  //        baseTime.plusDays(2),
  //        baseTime.plusDays(3),
  //        30L,
  //        1L,
  //        true
  //    ));
  //    dropsRepository.save(Drops.create(
  //        soonerProduct,
  //        baseTime.plusDays(1),
  //        baseTime.plusDays(2),
  //        30L,
  //        1L,
  //        true
  //    ));
  //    dropsRepository.flush();
  //
  //    Page<Product> page = productRepository.findPublicProductsOrderByDropImminent(
  //        EnumSet.of(ProductStatus.READY, ProductStatus.ON_SALE, ProductStatus.OUT_OF_STOCK),
  //        baseTime,
  //        PageRequest.of(0, 10)
  //    );
  //
  //    assertThat(page.getContent()).extracting(Product::getId)
  //        .containsSequence(soonerProduct.getId(), laterProduct.getId());
  //  }

  private Product saveProduct(
      Long sellerId, String name, BigDecimal price, int discountRate, ProductStatus status) {
    Product product =
        Product.create(
            sellerId,
            name,
            "SHOES",
            price,
            discountRate,
            100,
            "https://cdn.example.com/thumb.jpg",
            "설명",
            "스펙",
            "배송 정책",
            "환불 정책");
    product.updateStatusByDrop(status);
    return productRepository.saveAndFlush(product);
  }
}
