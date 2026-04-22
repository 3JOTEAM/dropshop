package com.example.dropshop.domain.queue.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class QueueToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String queueToken;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "queue_id")
  private Queue queue;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  /**
   * 대기열 토큰 생성자.
   * @param queueToken 대기열 토큰.
   * @param queue 대기열 엔티티.
   */
  public QueueToken(String queueToken, Queue queue) {
    this.queueToken = queueToken;
    this.queue = queue;
    this.createdAt = LocalDateTime.now();
  }
}
