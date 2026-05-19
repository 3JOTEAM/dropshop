package com.example.dropshop.domain.user.outbox;

/** 유저 이벤트 아웃박스 메시지 처리 상태. */
public enum UserEventOutboxStatus {

  /** 아직 Kafka로 발행되지 않은 초기 상태. */
  PENDING,

  /** Kafka 발행 성공. */
  SENT,

  /** Kafka 발행 실패 (재시도 대상). */
  FAILED
}
