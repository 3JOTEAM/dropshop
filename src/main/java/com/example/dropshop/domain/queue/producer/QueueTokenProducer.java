package com.example.dropshop.domain.queue.producer;

import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_QUEUE_TOKEN;

import com.example.dropshop.domain.queue.dto.response.ThreadHoldResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** 대기열 토큰 생성자. */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueTokenProducer {

  private final KafkaTemplate<String, Object> kafkaTemplate;

  /**
   * send.
   *
   * @param threadHoldResponse dto.
   */
  public void send(ThreadHoldResponse threadHoldResponse) {
    try {
      kafkaTemplate
          .send(TOPIC_QUEUE_TOKEN, threadHoldResponse)
          .whenComplete(
              (result, ex) -> {
                if (ex != null) {
                  log.warn("[QueueTokenProducer] 대기열 토큰 Kafka 발행 실패 - dropId: {}, cause: {}",
                      threadHoldResponse.getDropsId(), ex.getMessage());
                }
              });
    } catch (Exception e) {
      log.warn("[QueueTokenProducer] 대기열 토큰 Kafka 발행 요청 실패 - dropId: {}, cause: {}",
          threadHoldResponse.getDropsId(), e.getMessage());
    }
  }
}
