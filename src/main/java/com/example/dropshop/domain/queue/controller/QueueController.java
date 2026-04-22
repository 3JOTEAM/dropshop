package com.example.dropshop.domain.queue.controller;

import com.example.dropshop.domain.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 대기열 컨트롤러.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/queues")
public class QueueController {

  private final QueueService queueService;

  /**
   * 대기열 여부 결정
   * @param dropId 드랍 아이디.
   * @param userId 유저 아이디.
   */
  @PostMapping
  public void decideQueue(
      @RequestParam(name = "dropId") Long dropId,
      @RequestParam(name = "userId") Long userId
  ) {
    queueService.decideQueue(dropId, userId);
  }
}
