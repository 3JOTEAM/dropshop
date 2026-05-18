package com.example.dropshop.domain.user.outbox;

import com.example.dropshop.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유저 이벤트 아웃박스 테이블.
 *
 * <p>회원가입 트랜잭션과 동일한 DB 트랜잭션 내에서 저장되어, 브로커 장애 시에도 이벤트 유실 없이 재발행할 수 있도록 보장한다.
 *
 * <p>스케줄러({@link UserEventOutboxPublisher})가 PENDING 상태 레코드를 읽어 Kafka로 발행한 뒤 SENT로 마킹한다.
 */
@Entity
@Table(name = "user_event_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEventOutbox extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** 발행 대상 Kafka 토픽. */
  @Column(nullable = false, length = 100)
  private String topic;

  /** Kafka 파티셔닝 키. */
  @Column(nullable = false, length = 100)
  private String messageKey;

  /** 직렬화된 이벤트 JSON. */
  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserEventOutboxStatus status;

  /** 발행 시도 횟수. 재시도 추적에 사용한다. */
  @Column(nullable = false)
  private int attempts;

  /** 다음 재시도 허용 시각. null이면 즉시 처리 가능(PENDING 초기 상태). FAILED 전이 시 지수 백오프로 계산한 미래 시각이 설정된다. */
  @Column private LocalDateTime nextAttemptAt;

  public UserEventOutbox(String topic, String messageKey, String payload) {
    this.topic = topic;
    this.messageKey = messageKey;
    this.payload = payload;
    this.status = UserEventOutboxStatus.PENDING;
    this.attempts = 0;
  }

  /** Kafka 발행 성공 시 SENT로 전이한다. */
  public void markSent() {
    this.status = UserEventOutboxStatus.SENT;
  }

  /**
   * Kafka 발행 실패 시 시도 횟수를 증가시키고 FAILED로 전이한다.
   *
   * <p>지수 백오프로 다음 재시도 시각을 계산한다: 2^attempts 분 후.
   */
  public void markFailed() {
    this.attempts++;
    this.status = UserEventOutboxStatus.FAILED;
    long backoffMinutes = (long) Math.pow(2, this.attempts);
    this.nextAttemptAt = LocalDateTime.now().plusMinutes(backoffMinutes);
  }
}
