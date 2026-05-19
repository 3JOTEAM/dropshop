package com.example.dropshop.domain.refund.repository;

import com.example.dropshop.domain.refund.entity.Refund;
import com.example.dropshop.domain.refund.enums.RefundStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 환불 레포지토리. */
public interface RefundRepository extends JpaRepository<Refund, Long> {

  /**
   * 결제에 진행 중인 환불 요청이 있는지 확인한다.
   *
   * @param paymentId 결제 ID
   * @param statuses 조회할 환불 상태 목록
   * @return 진행 중인 환불 요청 존재 여부
   */
  boolean existsByPaymentIdAndStatusIn(Long paymentId, Collection<RefundStatus> statuses);

  /**
   * 결제에 연결된 환불 목록을 최신순으로 조회한다.
   *
   * @param paymentId 결제 ID
   * @return 환불 목록
   */
  List<Refund> findAllByPaymentIdOrderByCreatedAtDesc(Long paymentId);

  List<Refund> findAllByStatusAndModifiedAtBefore(RefundStatus status, LocalDateTime modifiedAt);
}
