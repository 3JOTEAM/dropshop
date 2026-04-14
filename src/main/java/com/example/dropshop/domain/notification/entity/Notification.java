package com.example.dropshop.domain.notification.entity;

import com.example.dropshop.domain.notification.enums.NotificationStatus;
import com.example.dropshop.domain.notification.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_user_created", columnList = "user_id, created_at DESC"),
        @Index(name = "idx_user_status_created", columnList = "user_id, status, created_at DESC")
    }
)
class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  private NotificationType type;

  private String message;

  @Enumerated(EnumType.STRING)
  private NotificationStatus status; // UNREAD, READ

  @Column(nullable = false)
  private Long productId;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  private LocalDateTime readAt;

  public Notification(Long userId, String type, String message, Long productId){
    this.userId = userId;
    this.type = NotificationType.from(type);
    this.message = message;
    this.status = NotificationStatus.UNREAD;
    this.productId = productId;
  }
}