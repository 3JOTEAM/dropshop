package com.example.dropshop.domain.notification.entity;

import com.example.dropshop.common.entity.BaseEntity;
import com.example.dropshop.domain.notification.enums.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notification_settings")
public class NotificationSetting extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long userId;

  private Long sellerId;

  @Column(nullable = false)
  private Boolean isDropEnabled;

  @Column(nullable = false)
  private Boolean isOrderEnabled;

  @Column(nullable = false)
  private Boolean isStockEnabled;

  @Column(nullable = false)
  private Boolean isReviewEnabled;

  public NotificationSetting(Long userId, Long sellerId, Boolean isDropEnabled, Boolean isOrderEnabled, Boolean isStockEnabled, Boolean isReviewEnabled){
    this.userId = userId;
    this.sellerId = sellerId;
    this.isDropEnabled = isDropEnabled;
    this.isOrderEnabled = isOrderEnabled;
    this.isStockEnabled = isStockEnabled;
    this.isReviewEnabled = isReviewEnabled;
  }
}
