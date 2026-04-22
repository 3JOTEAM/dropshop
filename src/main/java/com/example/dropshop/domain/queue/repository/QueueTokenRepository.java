package com.example.dropshop.domain.queue.repository;

import com.example.dropshop.domain.queue.entity.Queue;
import com.example.dropshop.domain.queue.entity.QueueToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * 대기열 토큰 리포지토리.
 */
public interface QueueTokenRepository extends CrudRepository<QueueToken, Long> {

  /**
   * 대기열 토큰.
   * @param queueId 대기열 아이디.
   * @return 리턴.
   */
  @Query("""
    SELECT qt
    FROM QueueToken qt
    WHERE qt.queue.id = :queueId
""")
  Optional<QueueToken> findByQueueId(@Param("queueId") Long queueId);
}
