package com.example.dropshop.domain.payment.dto.response;

import com.example.dropshop.domain.order.enums.OrderStatus;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentStatus;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 결제 확정 응답.
 */
@Getter
public class PaymentConfirmResponse {

  private final Long paymentId;
  private final Long orderId;
  private final String merchantPaymentId;
  private final String portOneTransactionId;
  private final PaymentStatus paymentStatus;
  private final OrderStatus orderStatus;
  private final LocalDateTime paidAt;

  private PaymentConfirmResponse(Payment payment, OrderStatus orderStatus) {
    this.paymentId = payment.getId();
    this.orderId = payment.getOrderId();
    this.merchantPaymentId = payment.getMerchantPaymentId();
    this.portOneTransactionId = payment.getPortOneTransactionId();
    this.paymentStatus = payment.getStatus();
    this.orderStatus = orderStatus;
    this.paidAt = payment.getPaidAt();
  }

  public static PaymentConfirmResponse of(Payment payment, OrderStatus orderStatus) {
    return new PaymentConfirmResponse(payment, orderStatus);
  }
}
