package com.example.dropshop.domain.notification.drops.producer;

import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_DROPS_STATUS_CHANGED;

import com.example.dropshop.domain.notification.drops.event.DropStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** 드랍 상태 변경 이벤트 생산자. */
@Service
@RequiredArgsConstructor
@Slf4j
public class DropsStatusChangedEventProducer {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  /** 드랍 상태 변경 이벤트를 발행한다. */
  public void send(DropStatusChangedEvent event) {
    String key = String.valueOf(event.getDropId());
    try {
      kafkaTemplate
          .send(TOPIC_DROPS_STATUS_CHANGED, key, event)
          .whenComplete(
              (result, exception) -> {
                if (exception != null) {
                  log.warn(
                      "드랍 상태 변경 이벤트 발행 실패. dropId={}, from={}, to={}",
                      event.getDropId(),
                      event.getFromStatus(),
                      event.getToStatus(),
                      exception);
                  return;
                }
                log.debug(
                    "드랍 상태 변경 이벤트 발행 성공. topic={}, partition={}, offset={}, dropId={}",
                    TOPIC_DROPS_STATUS_CHANGED,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    event.getDropId());
              });
    } catch (Exception e) {
      // 상태 전이 핵심 트랜잭션은 유지하고, 이벤트 발행 실패는 로그로 추적한다.
      log.warn(
          "드랍 상태 변경 이벤트 발행 요청 실패. dropId={}, from={}, to={}",
          event.getDropId(),
          event.getFromStatus(),
          event.getToStatus(),
          e);
    }
  }
}
