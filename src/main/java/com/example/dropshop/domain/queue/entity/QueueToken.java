package com.example.dropshop.domain.queue.entity;

import com.example.dropshop.domain.queue.enums.QueueStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@EntityListeners(AuditingEntityListener.class)
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
   *
   * @param queueToken 대기열 토큰.
   * @param queue      대기열 엔티티.
   */
  public QueueToken(String queueToken, Queue queue) {
    this.queueToken = queueToken;
    this.queue = queue;
    this.createdAt = LocalDateTime.now();
  }

  /**
   * 토큰 검증.
   *
   * @return 리턴.
   */
  public boolean validateToken() {
    return this.queue.getStatus() != QueueStatus.EXPIRED;
  }
}
