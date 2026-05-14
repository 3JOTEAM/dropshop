package com.example.dropshop.domain.notification.drops.entity;

import com.example.dropshop.common.entity.BaseEntity;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import com.example.dropshop.domain.notification.drops.exception.DropsException;
import com.example.dropshop.domain.product.entity.Product;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 드랍 엔티티. */
@Getter
@Entity
@Table(
    name = "drops",
    indexes = {
      @Index(name = "idx_drops_product_id", columnList = "product_id"),
      @Index(name = "idx_drops_product_created_at", columnList = "product_id, created_at"),
      @Index(name = "idx_drops_status_start_at", columnList = "status, start_at"),
      @Index(name = "idx_drops_status_end_at", columnList = "status, end_at"),
      @Index(name = "idx_drops_status_remain_stock", columnList = "status, remain_stock")
    })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Drops extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DropsStatus status;

  @Column(name = "start_at", nullable = false)
  private LocalDateTime startAt;

  @Column(name = "end_at", nullable = false)
  private LocalDateTime endAt;

  @Column(name = "total_stock", nullable = false)
  private Long totalStock;

  @Column(name = "remain_stock", nullable = false)
  private Long remainStock;

  @Column(name = "purchase_limit", nullable = false)
  private Long purchaseLimit;

  @Column(name = "use_queue", nullable = false)
  private boolean useQueue;

  @Column(name = "view_count", nullable = false)
  private Long viewCount;

  @Version private Long version;

  @Builder(access = AccessLevel.PRIVATE)
  private Drops(
      Product product,
      LocalDateTime startAt,
      LocalDateTime endAt,
      Long totalStock,
      Long remainStock,
      Long purchaseLimit,
      boolean useQueue) {
    this.product = product;
    this.startAt = startAt;
    this.endAt = endAt;
    this.totalStock = totalStock;
    this.remainStock = remainStock;
    this.purchaseLimit = purchaseLimit;
    this.useQueue = useQueue;
    this.status = DropsStatus.SCHEDULED;
    this.viewCount = 0L;

    validateDateRange(startAt, endAt);
    validateStock(totalStock, remainStock);
    validatePurchaseLimit(purchaseLimit);
  }

  /** 도메인 규칙을 검증하여 드랍을 생성한다. */
  public static Drops create(
      Product product,
      LocalDateTime startAt,
      LocalDateTime endAt,
      Long totalStock,
      Long purchaseLimit,
      boolean useQueue) {
    return Drops.builder()
        .product(product)
        .startAt(startAt)
        .endAt(endAt)
        .totalStock(totalStock)
        .remainStock(totalStock)
        .purchaseLimit(purchaseLimit)
        .useQueue(useQueue)
        .build();
  }

  /** 드랍 상태를 진행 중으로 변경한다. */
  public void activate() {
    this.status = DropsStatus.ACTIVE;
  }

  /** 드랍 상태를 종료로 변경한다. */
  public void finish() {
    this.status = DropsStatus.FINISHED;
  }

  /** 드랍 정보를 수정한다. */
  public void update(
      LocalDateTime startAt,
      LocalDateTime endAt,
      Long totalStock,
      Long remainStock,
      Long purchaseLimit,
      boolean useQueue) {
    validateDateRange(startAt, endAt);
    validateStock(totalStock, remainStock);
    validatePurchaseLimit(purchaseLimit);

    this.startAt = startAt;
    this.endAt = endAt;
    this.totalStock = totalStock;
    this.remainStock = remainStock;
    this.purchaseLimit = purchaseLimit;
    this.useQueue = useQueue;
  }

  /** 예정 상태 여부를 반환한다. */
  public boolean isScheduled() {
    return this.status == DropsStatus.SCHEDULED;
  }

  /** 진행 상태 여부를 반환한다. */
  public boolean isActive() {
    return this.status == DropsStatus.ACTIVE;
  }

  /** 종료 상태 여부를 반환한다. */
  public boolean isFinished() {
    return this.status == DropsStatus.FINISHED;
  }

  /** 잔여 재고를 차감한다. */
  public void decrementRemainStock(long quantity) {
    if (this.remainStock < quantity) {
      throw new DropsException(ErrorCode.INVALID_DROP_REMAIN_STOCK);
    }
    this.remainStock -= quantity;
  }

  /** 잔여 재고를 복구한다. */
  public void restoreRemainStock(long quantity) {
    this.remainStock += quantity;
    if (this.remainStock > this.totalStock) {
      throw new DropsException(ErrorCode.INVALID_DROP_REMAIN_STOCK);
    }
  }

  /** 조회수를 1 증가시킨다. */
  public void increaseViewCount() {
    this.viewCount += 1;
  }

  private void validateDateRange(LocalDateTime startAt, LocalDateTime endAt) {
    if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
      throw new DropsException(ErrorCode.INVALID_DROP_DATE_RANGE);
    }
  }

  private void validateStock(Long totalStock, Long remainStock) {
    if (totalStock == null || totalStock <= 0) {
      throw new DropsException(ErrorCode.INVALID_DROP_TOTAL_STOCK);
    }
    if (remainStock == null || remainStock < 0 || remainStock > totalStock) {
      throw new DropsException(ErrorCode.INVALID_DROP_REMAIN_STOCK);
    }
  }

  private void validatePurchaseLimit(Long purchaseLimit) {
    if (purchaseLimit == null || purchaseLimit <= 0) {
      throw new DropsException(ErrorCode.INVALID_DROP_PURCHASE_LIMIT);
    }
  }

  /** 남은 재고를 차감한다. */
  public void removeRemainStock(int quantity) {
    // [추가] 수량이 0 이하인 비정상적인 요청 차단
    if (quantity <= 0) {
      throw new DropsException(ErrorCode.INVALID_DROP_REMAIN_STOCK);
    }

    long restStock = this.remainStock - quantity;

    // 재고가 0보다 작아지면 예외 발생
    if (restStock < 0) {
      throw new DropsException(ErrorCode.INVALID_DROP_REMAIN_STOCK);
    }

    this.remainStock = restStock;

    // 재고가 0이 되면 드랍을 종료 상태로 변경
    if (this.remainStock == 0) {
      finish();
    }
  }
}
