package com.example.dropshop.domain.queue.producer;

import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_QUEUE_TOKEN;

import com.example.dropshop.domain.queue.dto.response.ThreadHoldResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/** 대기열 토큰 생성자. */
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
    kafkaTemplate.send(TOPIC_QUEUE_TOKEN, threadHoldResponse);
  }
}
