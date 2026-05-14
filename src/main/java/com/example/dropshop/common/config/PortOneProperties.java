package com.example.dropshop.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** PortOne 연동에 사용하는 설정 프로퍼티를 바인딩한다. */
@Validated
@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(
    String apiBaseUrl,
    @NotBlank(message = "PortOne API Secret은 필수입니다.") String apiSecret,
    String webhookSecret,
    @NotBlank(message = "PortOne storeId는 필수입니다.") String storeId,
    @NotBlank(message = "PortOne channelKey는 필수입니다.") String channelKey,
    @NotBlank(message = "PortOne redirectUrl은 필수입니다.") String redirectUrl,
    boolean mock) {

  /**
   * PortOne API 기본 URL을 반환한다.
   *
   * @return 기본 API URL
   */
  public String resolvedApiBaseUrl() {
    if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
      return "https://api.portone.io";
    }
    return apiBaseUrl;
  }

  /**
   * PortOne 웹훅 서명 검증에 사용할 secret을 반환한다.
   *
   * @return 웹훅 secret
   */
  public String resolvedWebhookSecret() {
    if (webhookSecret == null || webhookSecret.isBlank()) {
      return apiSecret;
    }
    return webhookSecret;
  }
}
