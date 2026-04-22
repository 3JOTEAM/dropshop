package com.example.dropshop.domain.queue.repository;

import com.example.dropshop.domain.queue.entity.Queue;
import com.example.dropshop.domain.queue.enums.QueueStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 대기열 리포지토리.
 */
public interface QueueRepository extends JpaRepository<Queue, Integer> {

  List<Queue> findDropIdAndUserId(Long dropId, Long userId);

  long countByDropIdAndStatusIn(Long dropId, List<QueueStatus> status);

  long countByDropIdAndStatusAndEnteredAtBefore(
      Long dropId,
      QueueStatus status,
      LocalDateTime enteredAt
  );

  List<Queue> findDropIdAndUserIdAndStatusIn(Long dropId, Long userId, Collection<QueueStatus> statuses);
}
