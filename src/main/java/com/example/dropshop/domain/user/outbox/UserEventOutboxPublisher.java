package com.example.dropshop.domain.user.outbox;

import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_USER_SIGNUP;

import com.example.dropshop.domain.user.event.UserSignupEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유저 이벤트 아웃박스 발행 서비스.
 *
 * <p>회원가입 트랜잭션과 동일한 DB 커밋에 이벤트를 저장하고, 스케줄러가 5초마다 Kafka로 발행한다. ShedLock으로 멀티 인스턴스 중복 실행을 방지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserEventOutboxPublisher {

  private static final int BATCH_SIZE = 50;
  private static final int MAX_ATTEMPTS = 5;
  private static final Set<UserEventOutboxStatus> RETRYABLE_STATUSES =
      Set.of(UserEventOutboxStatus.PENDING, UserEventOutboxStatus.FAILED);

  private final UserEventOutboxRepository outboxRepository;
  private final KafkaTemplate<String, Object> activityEventKafkaTemplate;
  private final ObjectMapper objectMapper;

  /** 회원가입 이벤트를 아웃박스 테이블에 저장한다. 호출부의 트랜잭션에 참여하여 DB 커밋과 원자적으로 처리된다. */
  @Transactional
  public void save(UserSignupEvent event, String messageKey) {
    String payload;
    try {
      payload = objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("[Outbox] 회원가입 이벤트 직렬화 실패 - email: " + event.getEmail(), e);
    }
    outboxRepository.save(new UserEventOutbox(TOPIC_USER_SIGNUP, messageKey, payload));
    log.debug("[Outbox] 회원가입 이벤트 저장 완료 - email: {}", event.getEmail());
  }

  @Scheduled(fixedDelay = 5000)
  @SchedulerLock(
      name = "userEventOutboxPublisher_publishPending",
      lockAtMostFor = "PT1M",
      lockAtLeastFor = "PT5S")
  @Transactional
  public void publishPending() {
    List<UserEventOutbox> candidates =
        outboxRepository.findRetryable(RETRYABLE_STATUSES, LocalDateTime.now(), MAX_ATTEMPTS);

    if (candidates.isEmpty()) {
      return;
    }

    log.info("[Outbox] 유저 이벤트 발행 시작 - 건수: {}", candidates.size());
    int successCount = 0;
    int failCount = 0;

    for (UserEventOutbox outbox : candidates) {
      try {
        UserSignupEvent event = objectMapper.readValue(outbox.getPayload(), UserSignupEvent.class);
        activityEventKafkaTemplate.send(outbox.getTopic(), outbox.getMessageKey(), event).get();
        outbox.markSent();
        successCount++;
      } catch (Exception e) {
        log.error(
            "[Outbox] 유저 이벤트 발행 실패 - outboxId: {}, attempts: {}/{}, cause: {}",
            outbox.getId(),
            outbox.getAttempts() + 1,
            MAX_ATTEMPTS,
            e.getMessage());
        outbox.markFailed();
        failCount++;
      }
    }

    log.info("[Outbox] 유저 이벤트 발행 완료 - 성공: {}, 실패: {}", successCount, failCount);
  }
}
