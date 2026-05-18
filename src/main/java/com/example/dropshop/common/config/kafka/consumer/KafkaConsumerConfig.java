package com.example.dropshop.common.config.kafka.consumer;

import static com.example.dropshop.common.constant.kafka.group.KafkaGroups.NOTIFICATION_PAYMENT_GROUP_NAME;
import static com.example.dropshop.common.constant.kafka.group.KafkaGroups.PAYMENT_STATS_GROUP_NAME;
import static com.example.dropshop.common.constant.kafka.group.KafkaGroups.QUEUE_GROUP_NAME;
import static com.example.dropshop.common.constant.kafka.group.KafkaGroups.READY_QUEUE_GROUP_NAME;
import static com.example.dropshop.common.constant.kafka.group.KafkaGroups.SELLER_APPLY_GROUP_NAME;
import static com.example.dropshop.common.constant.kafka.group.KafkaGroups.USER_LOGIN_GROUP_NAME;
import static com.example.dropshop.common.constant.kafka.group.KafkaGroups.USER_SIGNUP_GROUP_NAME;

import com.example.dropshop.domain.auth.event.UserLoginEvent;
import com.example.dropshop.domain.payment.event.PaymentStatusChangedEvent;
import com.example.dropshop.domain.queue.dto.response.ThreadHoldResponse;
import com.example.dropshop.domain.seller.event.SellerAppliedEvent;
import com.example.dropshop.domain.user.event.UserSignupEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.ExponentialBackOff;

/** 카프카 소비자 설정. */
@Configuration
public class KafkaConsumerConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  /** DLT 발행에 사용할 KafkaTemplate. ProducerConfig와의 순환 참조 방지를 위해 @Lazy 적용. */
  @Lazy @Autowired private KafkaTemplate<String, Object> activityEventKafkaTemplate;

  private Map<String, Object> getConsumerConfig(String groupName) {
    Map<String, Object> props = new HashMap<>();

    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, groupName);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ThreadHoldResponseKafkaDeserializer.class);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    return props;
  }

  @Bean
  public ConsumerFactory<String, ThreadHoldResponse> queueTokenConsumerFactory() {
    return new DefaultKafkaConsumerFactory<>(
        getConsumerConfig(QUEUE_GROUP_NAME),
        new StringDeserializer(),
        new ThreadHoldResponseKafkaDeserializer());
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, ThreadHoldResponse>
      threadHoldKafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, ThreadHoldResponse> factory =
        new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(queueTokenConsumerFactory());
    factory.setAutoStartup(false);

    return factory;
  }

  @Bean
  public ConsumerFactory<String, ThreadHoldResponse> readyQueueTokenConsumerFactory() {
    return new DefaultKafkaConsumerFactory<>(
        getConsumerConfig(READY_QUEUE_GROUP_NAME),
        new StringDeserializer(),
        new ThreadHoldResponseKafkaDeserializer());
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, ThreadHoldResponse>
      readyThreadHoldKafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<String, ThreadHoldResponse> factory =
        new ConcurrentKafkaListenerContainerFactory<>();

    factory.setConsumerFactory(readyQueueTokenConsumerFactory());
    factory.setAutoStartup(false);

    return factory;
  }

  // -------------------------------------------------------------------------
  // LocalDateTime 지원 ObjectMapper를 사용하는 이벤트용 공통 헬퍼
  // -------------------------------------------------------------------------

  private <T> ConcurrentKafkaListenerContainerFactory<String, T> createActivityFactory(
      Class<T> clazz, String groupId) {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    JsonDeserializer<T> deserializer = new JsonDeserializer<>(clazz, mapper);
    deserializer.addTrustedPackages("com.example.dropshop.*");

    Map<String, Object> props = getConsumerConfig(groupId);
    ConsumerFactory<String, T> consumerFactory =
        new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);

    // 실패한 메시지를 {topic}.DLT 토픽으로 발행하는 recoverer
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(activityEventKafkaTemplate);

    // 최대 3회 재시도: 1초 → 2초 → 4초 지수 백오프
    ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
    backOff.setMaxAttempts(3);

    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
    // 역직렬화/직렬화 실패는 재시도해도 의미 없으므로 즉시 DLT로 라우팅
    errorHandler.addNotRetryableExceptions(
        DeserializationException.class,
        org.apache.kafka.common.errors.SerializationException.class);

    ConcurrentKafkaListenerContainerFactory<String, T> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler);
    factory.setAutoStartup(false);
    return factory;
  }

  // -------------------------------------------------------------------------
  // UserLoginEvent Consumer
  // -------------------------------------------------------------------------

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, UserLoginEvent>
      userLoginKafkaListenerContainerFactory() {
    return createActivityFactory(UserLoginEvent.class, USER_LOGIN_GROUP_NAME);
  }

  // -------------------------------------------------------------------------
  // UserSignupEvent Consumer
  // -------------------------------------------------------------------------

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, UserSignupEvent>
      userSignupKafkaListenerContainerFactory() {
    return createActivityFactory(UserSignupEvent.class, USER_SIGNUP_GROUP_NAME);
  }

  // -------------------------------------------------------------------------
  // SellerAppliedEvent Consumer
  // -------------------------------------------------------------------------

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, SellerAppliedEvent>
      sellerApplyKafkaListenerContainerFactory() {
    return createActivityFactory(SellerAppliedEvent.class, SELLER_APPLY_GROUP_NAME);
  }

  // -------------------------------------------------------------------------
  // PaymentStatusChangedEvent Consumer (결제 통계)
  // -------------------------------------------------------------------------

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, PaymentStatusChangedEvent>
      paymentCompletedKafkaListenerContainerFactory() {
    return createActivityFactory(PaymentStatusChangedEvent.class, PAYMENT_STATS_GROUP_NAME);
  }

  // -------------------------------------------------------------------------
  // PaymentStatusChangedEvent Consumer (알림)
  // 별도 컨슈머 그룹 — 결제 통계 컨슈머와 독립적으로 동작한다.
  // -------------------------------------------------------------------------

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, PaymentStatusChangedEvent>
      notificationPaymentKafkaListenerContainerFactory() {
    return createActivityFactory(PaymentStatusChangedEvent.class, NOTIFICATION_PAYMENT_GROUP_NAME);
  }
}
