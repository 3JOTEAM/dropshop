package com.example.dropshop.domain.queue.dto.response;

import com.example.dropshop.domain.queue.enums.QueueStatus;

/**
 * 대기열 응답.
 */
public class QueueResponse {
  private Long userId;

  private Long dropId;

  private QueueStatus status;
}
