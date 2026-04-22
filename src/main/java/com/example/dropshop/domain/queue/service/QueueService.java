package com.example.dropshop.domain.queue.service;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.exception.ServiceException;
import com.example.dropshop.domain.drops.entity.Drops;
import com.example.dropshop.domain.drops.enums.DropsStatus;
import com.example.dropshop.domain.drops.repository.DropsRepository;
import com.example.dropshop.domain.queue.dto.response.ThreadHoldResponse;
import com.example.dropshop.domain.queue.entity.Queue;
import com.example.dropshop.domain.queue.entity.QueueToken;
import com.example.dropshop.domain.queue.enums.QueueStatus;
import com.example.dropshop.domain.queue.repository.QueueRepository;
import com.example.dropshop.domain.queue.repository.QueueTokenRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대기열 서비스.
 */
@Service
@RequiredArgsConstructor
public class QueueService {

  private final QueueRepository queueRepository;
  private final QueueTokenRepository queueTokenRepository;
  private final DropsRepository dropsRepository;

  private static final Long THRESHOLD = 10L;
  private static final Long PROCESS_TIME = 1L;

  /**
   * 대기열 direct, queue 결정 메소드
   * @param dropId 드랍 아이디.
   * @param userId 유저 아이디.
   */
  @Transactional
  public ThreadHoldResponse decideQueue(Long dropId, Long userId) {
    // 1. Drops의 Live 여부 확인

    Drops drop = dropsRepository.findById(dropId).orElseThrow(
        () -> new ServiceException(ErrorCode.DROP_NOT_FOUND)
    );

    // 2. Live가 아니라면 Blocked를 반환(예외)

    if (!drop.getStatus().equals(DropsStatus.ACTIVE)) {
      throw new ServiceException(ErrorCode.DROP_NOT_LIVE);
    }

    // 3. Live라면 기존 큐가 있는지 확인

    List<Queue> queueList = queueRepository.findDropIdAndUserIdAndStatusIn(dropId, userId,
        List.of(QueueStatus.WAITING, QueueStatus.READY, QueueStatus.ENTERED));

    long cnt = 0L;

    if (!queueList.isEmpty()){
      Queue queue = queueList.get(0);

      if (queue.getStatus().equals(QueueStatus.WAITING)) {
        // 3-1. 기존 큐의 상태가 WAITING이라면 기존 큐 반환

        long waitingCount = queueRepository.countByDropIdAndStatusAndEnteredAtBefore(
            dropId,
            QueueStatus.WAITING,
            queue.getEnteredAt()
        );

        int waitingTime = (int)(waitingCount / (double)THRESHOLD * PROCESS_TIME);

        return ThreadHoldResponse.queue(dropId, queue.getId(), waitingCount, waitingTime);
      }

      // 3-2. 기존 큐의 상태가 READY/ENTERED라면 구매 상태 반환

      QueueToken queueToken = queueTokenRepository.findByQueueId(queue.getId()).get();

      return ThreadHoldResponse.direct(dropId, queueToken.getQueueToken(), queueToken.getCreatedAt().plusMinutes(5), queue.getId());
    } else {
      // 3-3. 기존 큐의 상태가 없다면 해당 Drops의 전체 Queue의 수 Check

      Queue queue = new Queue(userId, dropId);
      cnt = queueRepository.countByDropIdAndStatusIn(dropId, List.of(QueueStatus.WAITING, QueueStatus.READY, QueueStatus.ENTERED));

      if (cnt < THRESHOLD) {
        // 3-3-1. 처리 허용 수 보다 작다면 토큰 발급, 큐 상태 READY 저장, 구매 상태로 반환

        String token = UUID.randomUUID().toString().replace("-", "");

        queue.ready();

        QueueToken queueToken = queueTokenRepository.save(new QueueToken(token, queue));

        return ThreadHoldResponse.direct(dropId, token, queueToken.getCreatedAt().plusMinutes(5), queue.getId());
      } else {
        // 3-3-2. 처리 허용 수 보다 크거나 같다면 큐 WAITING 저장, 대기 인원 수 계산, 예상 대기시간 계산, 큐 반환

        queueRepository.save(queue);

        int waitingTime = (int)(cnt / (double)THRESHOLD * PROCESS_TIME);

        return ThreadHoldResponse.queue(dropId, queue.getId(), cnt, waitingTime);
      }
    }
  }
}
