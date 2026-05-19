package com.example.dropshop.domain.user.outbox;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 유저 이벤트 아웃박스 레포지토리. */
public interface UserEventOutboxRepository extends JpaRepository<UserEventOutbox, Long> {

  /**
   * PENDING이거나 재시도 가능한 FAILED 레코드를 최대 50건 조회한다.
   *
   * @param statuses 조회할 상태 목록
   * @param now 현재 시각
   * @param maxAttempts 최대 시도 횟수
   * @return 처리 대상 아웃박스 레코드 목록
   */
  @Query(
      """
          SELECT o FROM UserEventOutbox o
          WHERE o.status IN :statuses
            AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now)
            AND o.attempts < :maxAttempts
          ORDER BY o.createdAt ASC
          LIMIT 50
          """)
  List<UserEventOutbox> findRetryable(
      @Param("statuses") Collection<UserEventOutboxStatus> statuses,
      @Param("now") LocalDateTime now,
      @Param("maxAttempts") int maxAttempts);
}
