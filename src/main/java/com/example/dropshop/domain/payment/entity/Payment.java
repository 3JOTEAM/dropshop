package com.example.dropshop.domain.payment.entity;

import com.example.dropshop.common.entity.BaseEntity;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.payment.enums.PaymentMethod;
import com.example.dropshop.domain.payment.enums.PaymentStatus;
import com.example.dropshop.domain.payment.exception.PaymentException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 결제 엔티티. */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private Long orderId;

  @Column(nullable = false, unique = true, length = 100)
  private String merchantPaymentId;

  @Column(name = "transaction_id")
  private String portOneTransactionId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private PaymentMethod paymentMethod;

  @Column(nullable = false)
  private BigDecimal amount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentStatus status;

  @Column private LocalDateTime paidAt;

  /** 결제 준비. */
  public static Payment prepare(
      Long orderId, String merchantPaymentId, PaymentMethod paymentMethod, BigDecimal amount) {
    Payment payment = new Payment();
    payment.orderId = orderId;
    payment.merchantPaymentId = merchantPaymentId;
    payment.paymentMethod = paymentMethod;
    payment.amount = amount;
    payment.status = PaymentStatus.PENDING;
    return payment;
  }

  /** 결제 실패. */
  public void fail() {
    if (this.status != PaymentStatus.PENDING) {
      throw new PaymentException(ErrorCode.PAYMENT_FAIL_NOT_ALLOWED);
    }
    this.status = PaymentStatus.FAILED;
  }

  /** 결제 완료. */
  public void complete(String portOneTransactionId) {
    if (this.status != PaymentStatus.PENDING) {
      throw new PaymentException(ErrorCode.PAYMENT_COMPLETE_NOT_ALLOWED);
    }
    if (portOneTransactionId == null || portOneTransactionId.isBlank()) {
      throw new PaymentException(ErrorCode.PAYMENT_TRANSACTION_ID_REQUIRED);
    }
    this.portOneTransactionId = portOneTransactionId;
    this.paidAt = LocalDateTime.now();
    this.status = PaymentStatus.COMPLETED;
  }
}
