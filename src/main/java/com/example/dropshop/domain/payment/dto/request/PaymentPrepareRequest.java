package com.example.dropshop.domain.payment.dto.request;

import com.example.dropshop.domain.payment.enums.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * 결제 준비 요청.
 */
@Getter
public class PaymentPrepareRequest {

  @NotNull
  @Positive
  private Long orderId;

  @NotNull
  @Positive
  private BigDecimal amount;

  @NotBlank
  @Size(max = 40, message = "merchantPaymentId는 40자 이하여야 합니다.")
  private String merchantPaymentId;

  @NotNull
  private PaymentMethod paymentMethod;
}
