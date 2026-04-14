package com.example.dropshop.domain.queue.entity;

import com.example.dropshop.domain.queue.enums.QueueStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
    name = "queues",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_drop", columnNames = {"user_id", "drop_id"})
    },
    indexes = {
        @Index(name = "idx_drop_status_entered", columnList = "drop_id, status, entered_at")
    }
)
public class Queue {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long userId;

  @Column(nullable = false)
  private Long dropId;

  @Enumerated(EnumType.STRING)
  private QueueStatus status; // WAITING, ALLOWED, EXPIRED

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime enteredAt;

  private LocalDateTime allowedAt;

  private LocalDateTime expiredAt;

  public Queue(Long userId, Long dropId){
    this.userId = userId;
    this.dropId = dropId;
    this.status = QueueStatus.WAITING;
  }
}