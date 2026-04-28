package com.example.dropshop.domain.payment.dto.response;

import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentMethod;
import com.example.dropshop.domain.payment.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 결제 준비 응답.
 */
@Getter
public class PaymentPrepareResponse {

  private final Long paymentId;
  private final Long orderId;
  private final String merchantPaymentId;
  private final PaymentMethod paymentMethod;
  private final BigDecimal amount;
  private final PaymentStatus status;
  private final LocalDateTime createdAt;

  private PaymentPrepareResponse(Payment payment) {
    this.paymentId = payment.getId();
    this.orderId = payment.getOrderId();
    this.merchantPaymentId = payment.getMerchantPaymentId();
    this.paymentMethod = payment.getPaymentMethod();
    this.amount = payment.getAmount();
    this.status = payment.getStatus();
    this.createdAt = payment.getCreatedAt();
  }

  public static PaymentPrepareResponse from(Payment payment) {
    return new PaymentPrepareResponse(payment);
  }
}
