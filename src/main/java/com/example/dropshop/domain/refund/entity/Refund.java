package com.example.dropshop.domain.refund.entity;

import com.example.dropshop.common.entity.BaseEntity;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.refund.exception.RefundException;
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

/**
 * 환불 엔티티.
 */
@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long paymentId;

  @Column(nullable = false)
  private BigDecimal refundAmount;

  @Column
  private String refundReason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RefundStatus status;

  @Column
  private LocalDateTime approvedAt;

  @Column
  private LocalDateTime completedAt;

  /**
   * 환불 생성.
   *
   * @param paymentId 결제 ID
   * @param refundAmount 환불 금액
   * @param refundReason 환불 사유
   * @return 생성된 환불 엔티티
   */
  public static Refund create(Long paymentId, BigDecimal refundAmount, String refundReason) {
    Refund refund = new Refund();
    refund.paymentId = paymentId;
    refund.refundAmount = refundAmount;
    refund.refundReason = refundReason;
    refund.status = RefundStatus.PENDING;
    return refund;
  }

  /**
   * 환불 거절.
   */
  public void reject() {
    this.status = RefundStatus.REJECTED;
  }

  /**
   * 환불 승인.
   */
  public void approve() {
    this.approvedAt = LocalDateTime.now();
    this.status = RefundStatus.APPROVED;
  }

  /**
   * 외부 PG 환불 처리 시작.
   */
  public void startProcessing() {
    if (this.status != RefundStatus.APPROVED) {
      throw new RefundException(ErrorCode.REFUND_INVALID_STATUS);
    }
    this.status = RefundStatus.PROCESSING;
  }

  /**
   * 외부 PG 환불 실패로 승인 상태로 복구.
   */
  public void revertToApproved() {
    if (this.status != RefundStatus.PROCESSING) {
      throw new RefundException(ErrorCode.REFUND_INVALID_STATUS);
    }
    this.status = RefundStatus.APPROVED;
  }

  /**
   * 환불 완료.
   */
  public void complete() {
    if (this.status != RefundStatus.PROCESSING) {
      throw new RefundException(ErrorCode.REFUND_INVALID_STATUS);
    }
    this.completedAt = LocalDateTime.now();
    this.status = RefundStatus.COMPLETED;
  }
}
