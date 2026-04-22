package com.example.dropshop.domain.queue.enums;

/**
 * 대기열 반환 응답 result.
 */
public enum ThreadHoldResult {
  DIRECT("Drops로 바로 이동"),
  QUEUE("대기열에 추가되어 대기"),
  EXPIRED("만료");

  private String description;

  ThreadHoldResult(String description) {
    this.description = description;
  }
}
