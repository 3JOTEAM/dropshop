package com.example.dropshop.domain.notification.drops.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 드랍 진행 상태. */
@Getter
@RequiredArgsConstructor
public enum DropsStatus {
  SCHEDULED("예정"),
  ACTIVE("진행"),
  FINISHED("종료");

  private final String description;
}
