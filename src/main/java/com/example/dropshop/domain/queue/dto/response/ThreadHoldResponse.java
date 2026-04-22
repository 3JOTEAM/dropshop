package com.example.dropshop.domain.queue.dto.response;

import com.example.dropshop.domain.queue.enums.ThreadHoldResult;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 대기열 반환 응답 큐
 */
@Getter
public class ThreadHoldResponse {
  // 입장 요청에 대한 결과
  private ThreadHoldResult result;

  // 입장 하려는 drops의 id
  private Long dropsId;

  // 입장하며 사용할 토큰
  private String admissionToken;

  // 토큰의 남은 시간 (초)
  private int expiresInSeconds;

  // 큐 id
  private Long queueId;

  // 앞서 대기중인 세션 수
  private long aheadCount;

  // 예상 대기 시간 (초)
  private int estimatedWaitSeconds;

  // 서버 기준 시간
  private LocalDateTime serverTime;

  /**
   * 대기열 direct.
   * @param dropsId 드랍 아이디.
   * @param admissionToken 입장 토큰.
   * @param expiresAt 만료 일자.
   * @param queueId 대기열 아이디.
   * @return 리턴.
   */
  public static ThreadHoldResponse direct(
      Long dropsId, String admissionToken, LocalDateTime expiresAt, Long queueId
  ) {
    ThreadHoldResponse response = new ThreadHoldResponse();
    response.result = ThreadHoldResult.DIRECT;
    response.dropsId = dropsId;
    response.admissionToken = admissionToken;
    response.expiresInSeconds = (int) Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
    response.queueId = queueId;
    response.serverTime = LocalDateTime.now();

    return response;
  }

  /**
   * 대기열 queue
   * @param dropsId 드랍 아이디.
   * @param queueId 대기열 아이디.
   * @param aheadCount 대기 세션 수.
   * @param estimatedWaitSeconds 예상 대기 시간.
   * @return 리턴.
   */
  public static ThreadHoldResponse queue(
      Long dropsId, Long queueId, long aheadCount, int estimatedWaitSeconds
  ) {
    ThreadHoldResponse response = new ThreadHoldResponse();
    response.result = ThreadHoldResult.QUEUE;
    response.dropsId = dropsId;
    response.queueId = queueId;
    response.aheadCount = aheadCount;
    response.estimatedWaitSeconds = estimatedWaitSeconds;
    response.serverTime = LocalDateTime.now();

    return response;
  }
}
