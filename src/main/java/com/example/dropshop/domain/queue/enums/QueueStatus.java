package com.example.dropshop.domain.queue.enums;

import lombok.Getter;

/**
 * 대기열 상태.
 */
@Getter
public enum QueueStatus {
  WAITING("대기 중"),
  READY("입장 가능"),
  ENTERED("상세 진입 완료"),
  EXPIRED("시간 초과"),
  BLOCKED("진입 불가");

  private final String description;

  QueueStatus(String description) {
    this.description = description;
  }
}

