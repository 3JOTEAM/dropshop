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
import org.springframework.data.annotation.CreatedDate;

/**
 * 대기열 Entity.
 */
@Getter
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
  @Column(nullable = false)
  private QueueStatus status; // WAITING, ALLOWED, EXPIRED

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime enteredAt;

  private LocalDateTime allowedAt;

  private LocalDateTime expiredAt;

  /**
   * 대기열 생성자.
   * @param userId 유저 아이디.
   * @param dropId 드랍 아이디.
   */
  public Queue(Long userId, Long dropId) {
    this.userId = userId;
    this.dropId = dropId;
    this.status = QueueStatus.WAITING;
  }

  /**
   * 대기열 ready 변경.
   */
  public void ready() {
    this.status = QueueStatus.READY;
  }

  /**
   * 대기열 entered 변경.
   */
  public void enter() {
    this.status = QueueStatus.ENTERED;
  }

  /**
   * 대기열 expired 변경.
   */
  public void expire() {
    this.status = QueueStatus.EXPIRED;
    this.expiredAt = LocalDateTime.now();
  }
}