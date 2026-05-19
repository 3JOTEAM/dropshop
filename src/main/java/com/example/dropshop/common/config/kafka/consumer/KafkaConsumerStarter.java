package com.example.dropshop.common.config.kafka.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

/** Tomcat 기동 완료 후 Kafka consumer를 일괄 시작해 앱 기동 시간을 단축한다. */
@Component
@RequiredArgsConstructor
public class KafkaConsumerStarter {

  private final KafkaListenerEndpointRegistry registry;

  @EventListener(ApplicationReadyEvent.class)
  public void startConsumers() {
    registry.start();
  }
}
