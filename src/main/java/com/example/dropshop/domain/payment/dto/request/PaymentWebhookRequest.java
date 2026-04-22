package com.example.dropshop.domain.payment.dto.request;

import java.util.Map;
import lombok.Getter;

/**
 * PortOne 웹훅 요청 바디.
 */
@Getter
public class PaymentWebhookRequest {

  private String id;
  private String paymentId;
  private Map<String, Object> data;

  /**
   * 웹훅 바디에서 PortOne 결제 식별자를 추출한다.
   */
  public String extractPortOnePaymentId() {
    if (hasText(id)) {
      return id;
    }
    if (hasText(paymentId)) {
      return paymentId;
    }
    if (data != null) {
      Object dataId = data.get("id");
      if (hasText(asText(dataId))) {
        return asText(dataId);
      }
      Object dataPaymentId = data.get("paymentId");
      if (hasText(asText(dataPaymentId))) {
        return asText(dataPaymentId);
      }
    }
    return null;
  }

  private String asText(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
