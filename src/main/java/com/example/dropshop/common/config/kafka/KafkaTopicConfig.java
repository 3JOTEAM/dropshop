package com.example.dropshop.common.config.kafka;

import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_DROPS_STATUS_CHANGED;
import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_ORDER_CANCELLED;
import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_ORDER_PAID;
import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_ORDER_REFUNDED;
import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_ORDER_STOCK_RESTORED;
import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_PAYMENT_COMPLETED;
import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_PAYMENT_FAILED;
import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_QUEUE_TOKEN;
import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_READY_QUEUE_TOKEN;
import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_SELLER_APPLY;
import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_USER_LOGIN;
import static com.example.dropshop.common.constant.kafka.topic.KafkaTopics.TOPIC_USER_SIGNUP;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * 애플리케이션 시작 시 Kafka 토픽을 자동 생성하는 설정.
 *
 * <p>Spring의 KafkaAdmin이 컨텍스트에서 NewTopic 빈을 감지해 토픽이 없을 경우 자동으로 생성한다(이미 존재하면 스킵).
 *
 * <p>파티션 수 / 복제 인수는 환경에 맞게 조정할 것: - 로컬 단일 브로커: replication factor 1 - 운영(3-브로커 클러스터): replication
 * factor 3
 */
@Configuration
public class KafkaTopicConfig {

  @Value("${spring.kafka.topic.partitions:3}")
  private int partitions;

  /** 운영 환경에서는 단일 브로커 장애 시 데이터 유실 방지를 위해 2 이상으로 설정할 것. */
  @Value("${spring.kafka.topic.replication-factor:1}")
  private short replicationFactor;

  /** 로그인 이벤트 토픽. Consumer: UserActivityKafkaConsumer#handleUserLogin */
  @Bean
  public NewTopic userLoginTopic() {
    return TopicBuilder.name(TOPIC_USER_LOGIN)
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  /** 회원가입 이벤트 토픽. Consumer: UserActivityKafkaConsumer#handleUserSignup */
  @Bean
  public NewTopic userSignupTopic() {
    return TopicBuilder.name(TOPIC_USER_SIGNUP)
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  /** 판매자 신청 이벤트 토픽. Consumer: SellerApplyKafkaConsumer#handleSellerApply */
  @Bean
  public NewTopic sellerApplyTopic() {
    return TopicBuilder.name(TOPIC_SELLER_APPLY)
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  /**
   * DLT(Dead Letter Topic) - 재시도 소진 후 실패 메시지가 라우팅되는 토픽. DeadLetterPublishingRecoverer는 기본적으로
   * {topic}.DLT 이름을 사용한다.
   */
  @Bean
  public NewTopic userLoginDltTopic() {
    return TopicBuilder.name(TOPIC_USER_LOGIN + ".DLT")
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  @Bean
  public NewTopic userSignupDltTopic() {
    return TopicBuilder.name(TOPIC_USER_SIGNUP + ".DLT")
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  @Bean
  public NewTopic sellerApplyDltTopic() {
    return TopicBuilder.name(TOPIC_SELLER_APPLY + ".DLT")
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  /** 결제 완료 이벤트 DLT. Consumer: PaymentStatisticsKafkaConsumer#handlePaymentCompleted */
  @Bean
  public NewTopic paymentCompletedDltTopic() {
    return TopicBuilder.name(TOPIC_PAYMENT_COMPLETED + ".DLT")
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  /** 대기열 토큰 토픽. Consumer: QueueTokenListener */
  @Bean
  public NewTopic queueTokenTopic() {
    return TopicBuilder.name(TOPIC_QUEUE_TOKEN)
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  /** 준비된 대기열 토큰 토픽. Consumer: ReadyQueueTokenListener */
  @Bean
  public NewTopic readyQueueTokenTopic() {
    return TopicBuilder.name(TOPIC_READY_QUEUE_TOKEN)
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  /** 결제 실패 이벤트 토픽. */
  @Bean
  public NewTopic paymentFailedTopic() {
    return TopicBuilder.name(TOPIC_PAYMENT_FAILED)
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  /** 드랍 상태 변경 이벤트 토픽. */
  @Bean
  public NewTopic dropsStatusChangedTopic() {
    return TopicBuilder.name(TOPIC_DROPS_STATUS_CHANGED)
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  /** 주문 결제 완료 이벤트 토픽. */
  @Bean
  public NewTopic orderPaidTopic() {
    return TopicBuilder.name(TOPIC_ORDER_PAID)
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  /** 주문 취소 이벤트 토픽. */
  @Bean
  public NewTopic orderCancelledTopic() {
    return TopicBuilder.name(TOPIC_ORDER_CANCELLED)
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  /** 주문 환불 이벤트 토픽. */
  @Bean
  public NewTopic orderRefundedTopic() {
    return TopicBuilder.name(TOPIC_ORDER_REFUNDED)
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }

  /** 주문 재고 복구 이벤트 토픽. */
  @Bean
  public NewTopic orderStockRestoredTopic() {
    return TopicBuilder.name(TOPIC_ORDER_STOCK_RESTORED)
        .partitions(partitions)
        .replicas(replicationFactor)
        .build();
  }
}
