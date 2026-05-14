package com.example.dropshop.domain.drops.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dropshop.common.config.QuerydslConfig;
import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import com.example.dropshop.domain.notification.drops.repository.DropsRepository;
import com.example.dropshop.domain.product.entity.Product;
import com.example.dropshop.domain.product.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:drops-test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.sql.init.mode=never"
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuerydslConfig.class)
class DropsRepositoryIntegrationTest {

  @Autowired private DropsRepository dropsRepository;

  @Autowired private ProductRepository productRepository;

  @Autowired private EntityManager entityManager;

  @Test
  @DisplayName("상태 필터로 공개 드랍 목록을 페이징 조회한다")
  void findAllByStatusIn_filtersCorrectly() {
    Product product = saveProduct(1L);

    Drops scheduled =
        saveDrop(
            product,
            DropsStatus.SCHEDULED,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(2));
    Drops active =
        saveDrop(
            product,
            DropsStatus.ACTIVE,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(2));
    Drops finished =
        saveDrop(
            product,
            DropsStatus.FINISHED,
            LocalDateTime.now().minusDays(2),
            LocalDateTime.now().minusDays(1));

    Page<Drops> result =
        dropsRepository.findAllByStatusIn(
            EnumSet.of(DropsStatus.SCHEDULED, DropsStatus.ACTIVE), PageRequest.of(0, 10));

    assertThat(result.getContent())
        .extracting(Drops::getId)
        .containsExactlyInAnyOrder(scheduled.getId(), active.getId());
    assertThat(result.getContent()).extracting(Drops::getId).doesNotContain(finished.getId());
  }

  @Test
  @DisplayName("findAllByStatusIn 조회 시 Product를 eager loading한다")
  void findAllByStatusIn_loadsProductEagerly() {
    Product product = saveProduct(1L);
    saveDrop(
        product,
        DropsStatus.ACTIVE,
        LocalDateTime.now().minusHours(1),
        LocalDateTime.now().plusHours(2));

    entityManager.clear();

    Page<Drops> result =
        dropsRepository.findAllByStatusIn(EnumSet.of(DropsStatus.ACTIVE), PageRequest.of(0, 10));

    PersistenceUnitUtil persistenceUnitUtil =
        entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
    Drops loaded = result.getContent().get(0);
    assertThat(persistenceUnitUtil.isLoaded(loaded, "product")).isTrue();
  }

  @Test
  @DisplayName("시작 시간이 도달한 SCHEDULED 드랍을 조회한다")
  void findAllByStatusAndStartAtLessThanEqual_returnsReadyDrops() {
    Product product = saveProduct(1L);
    LocalDateTime now = LocalDateTime.now();

    Drops ready = saveDrop(product, DropsStatus.SCHEDULED, now.minusMinutes(10), now.plusDays(1));
    Drops notYet = saveDrop(product, DropsStatus.SCHEDULED, now.plusHours(1), now.plusDays(1));

    List<Drops> result =
        dropsRepository.findAllByStatusAndStartAtLessThanEqual(DropsStatus.SCHEDULED, now);

    assertThat(result).extracting(Drops::getId).containsExactly(ready.getId());
    assertThat(result).extracting(Drops::getId).doesNotContain(notYet.getId());
  }

  @Test
  @DisplayName("종료 시간 초과 또는 재고 소진된 ACTIVE 드랍을 단일 쿼리로 조회한다")
  void findAllActiveDropsToFinish_returnsExpiredAndSoldOut() {
    Product product = saveProduct(1L);
    LocalDateTime now = LocalDateTime.now();

    Drops expired = saveDrop(product, DropsStatus.ACTIVE, now.minusDays(2), now.minusMinutes(1));
    Drops soldOut =
        saveSoldOutDrop(product, DropsStatus.ACTIVE, now.minusHours(1), now.plusHours(1));
    Drops ongoing = saveDrop(product, DropsStatus.ACTIVE, now.minusHours(1), now.plusHours(1));

    List<Drops> result = dropsRepository.findAllActiveDropsToFinish(DropsStatus.ACTIVE, now);

    assertThat(result)
        .extracting(Drops::getId)
        .containsExactlyInAnyOrder(expired.getId(), soldOut.getId());
    assertThat(result).extracting(Drops::getId).doesNotContain(ongoing.getId());
  }

  @Test
  @DisplayName("판매자 ID로 본인 드랍 목록을 조회한다")
  void findSellerDropsBySellerId_returnsOnlySellerDrops() {
    Product myProduct = saveProduct(1L);
    Product otherProduct = saveProduct(2L);

    Drops myDrop1 =
        saveDrop(
            myProduct,
            DropsStatus.FINISHED,
            LocalDateTime.now().minusDays(3),
            LocalDateTime.now().minusDays(2));
    Drops myDrop2 =
        saveDrop(
            myProduct,
            DropsStatus.SCHEDULED,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(2));
    Drops otherDrop =
        saveDrop(
            otherProduct,
            DropsStatus.ACTIVE,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(2));

    Page<Drops> result = dropsRepository.findSellerDropsBySellerId(1L, PageRequest.of(0, 10));

    assertThat(result.getContent())
        .extracting(Drops::getId)
        .containsExactlyInAnyOrder(myDrop1.getId(), myDrop2.getId());
    assertThat(result.getContent()).extracting(Drops::getId).doesNotContain(otherDrop.getId());
  }

  @Test
  @DisplayName("특정 상품의 드랍 이력을 공개 상태로 필터링하여 조회한다")
  void findAllByProductIdAndStatusIn_filtersCorrectly() {
    Product product = saveProduct(1L);

    Drops active =
        saveDrop(
            product,
            DropsStatus.ACTIVE,
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().plusHours(2));
    Drops finished =
        saveDrop(
            product,
            DropsStatus.FINISHED,
            LocalDateTime.now().minusDays(2),
            LocalDateTime.now().minusDays(1));
    Drops scheduled =
        saveDrop(
            product,
            DropsStatus.SCHEDULED,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(2));

    Page<Drops> result =
        dropsRepository.findAllByProductIdAndStatusIn(
            product.getId(),
            EnumSet.of(DropsStatus.ACTIVE, DropsStatus.FINISHED),
            PageRequest.of(0, 10));

    assertThat(result.getContent())
        .extracting(Drops::getId)
        .containsExactlyInAnyOrder(active.getId(), finished.getId());
    assertThat(result.getContent()).extracting(Drops::getId).doesNotContain(scheduled.getId());
  }

  @Test
  @DisplayName("특정 상품의 최신 드랍 1건을 시작 시간 기준 내림차순으로 조회한다")
  void findTopByProductIdOrderByStartAtDesc_returnsLatest() {
    Product product = saveProduct(1L);
    LocalDateTime now = LocalDateTime.now();

    saveDrop(product, DropsStatus.FINISHED, now.minusDays(5), now.minusDays(4));
    Drops latest = saveDrop(product, DropsStatus.SCHEDULED, now.plusDays(1), now.plusDays(2));

    Optional<Drops> result = dropsRepository.findTopByProductIdOrderByStartAtDesc(product.getId());

    assertThat(result).isPresent();
    assertThat(result.get().getId()).isEqualTo(latest.getId());
  }

  @Test
  @DisplayName("진행 중 상태 드랍 존재 여부를 확인한다")
  void existsByProductIdAndStatusIn_returnsTrue() {
    Product product = saveProduct(1L);
    saveDrop(
        product,
        DropsStatus.ACTIVE,
        LocalDateTime.now().minusHours(1),
        LocalDateTime.now().plusHours(2));

    boolean exists =
        dropsRepository.existsByProductIdAndStatusIn(
            product.getId(), EnumSet.of(DropsStatus.SCHEDULED, DropsStatus.ACTIVE));

    assertThat(exists).isTrue();
  }

  @Test
  @DisplayName("진행 중 드랍이 없으면 존재 여부가 false를 반환한다")
  void existsByProductIdAndStatusIn_returnsFalse() {
    Product product = saveProduct(1L);
    saveDrop(
        product,
        DropsStatus.FINISHED,
        LocalDateTime.now().minusDays(2),
        LocalDateTime.now().minusDays(1));

    boolean exists =
        dropsRepository.existsByProductIdAndStatusIn(
            product.getId(), EnumSet.of(DropsStatus.SCHEDULED, DropsStatus.ACTIVE));

    assertThat(exists).isFalse();
  }

  private Product saveProduct(Long sellerId) {
    Product product =
        Product.create(
            sellerId,
            "테스트 상품",
            "SHOES",
            new BigDecimal("100000"),
            10,
            100,
            "https://cdn.example.com/thumb.jpg",
            "설명",
            "스펙",
            "배송 정책",
            "환불 정책");
    return productRepository.saveAndFlush(product);
  }

  private Drops saveDrop(
      Product product, DropsStatus status, LocalDateTime startAt, LocalDateTime endAt) {
    Drops drops = Drops.create(product, startAt, endAt, 10L, 1L, true);
    if (status == DropsStatus.ACTIVE) {
      drops.activate();
    } else if (status == DropsStatus.FINISHED) {
      drops.activate();
      drops.finish();
    }
    return dropsRepository.saveAndFlush(drops);
  }

  private Drops saveSoldOutDrop(
      Product product, DropsStatus status, LocalDateTime startAt, LocalDateTime endAt) {
    Drops drops = Drops.create(product, startAt, endAt, 10L, 1L, true);
    if (status == DropsStatus.ACTIVE) {
      drops.activate();
    }
    drops.decrementRemainStock(10L);
    return dropsRepository.saveAndFlush(drops);
  }
}
