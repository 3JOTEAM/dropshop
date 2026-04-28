package com.example.dropshop.domain.payment.dto.response;

import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentMethod;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * PortOne 결제 요청 정보 응답.
 */
@Getter
public class PaymentPortOneRequestResponse {

  private final Long paymentId;
  private final Long orderId;
  private final String storeId;
  private final String channelKey;
  private final String merchantPaymentId;
  private final String orderName;
  private final String currency;
  private final String payMethod;
  private final PaymentMethod paymentMethod;
  private final BigDecimal amount;
  private final String redirectUrl;

  private PaymentPortOneRequestResponse(
      Payment payment,
      String storeId,
      String channelKey,
      String orderName,
      String redirectUrl
  ) {
    this.paymentId = payment.getId();
    this.orderId = payment.getOrderId();
    this.storeId = storeId;
    this.channelKey = channelKey;
    this.merchantPaymentId = payment.getMerchantPaymentId();
    this.orderName = orderName;
    this.currency = "CURRENCY_KRW";
    this.payMethod = payment.getPaymentMethod().name();
    this.paymentMethod = payment.getPaymentMethod();
    this.amount = payment.getAmount();
    this.redirectUrl = redirectUrl;
  }

  public static PaymentPortOneRequestResponse of(
      Payment payment,
      String storeId,
      String channelKey,
      String orderName,
      String redirectUrl
  ) {
    return new PaymentPortOneRequestResponse(
        payment,
        storeId,
        channelKey,
        orderName,
        redirectUrl
    );
  }
}
