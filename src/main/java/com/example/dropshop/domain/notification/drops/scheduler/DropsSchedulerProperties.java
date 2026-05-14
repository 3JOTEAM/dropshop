package com.example.dropshop.domain.notification.drops.scheduler;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 드랍 스케줄러 설정값을 바인딩한다. */
@Getter
@Validated
@ConfigurationProperties(prefix = "drops.scheduler")
public class DropsSchedulerProperties {

  private final boolean enabled;
  private final long fixedDelayMillis;

  /**
   * 드랍 스케줄러 설정값을 생성한다.
   *
   * @param enabled 스케줄러 활성화 여부
   * @param fixedDelayMillis 스케줄러 실행 주기(ms)
   */
  @SuppressWarnings("checkstyle:MissingJavadocMethod")
  public DropsSchedulerProperties(
      @DefaultValue("true") boolean enabled,
      @DefaultValue("30000") @Positive long fixedDelayMillis) {
    this.enabled = enabled;
    this.fixedDelayMillis = fixedDelayMillis;
  }
}
