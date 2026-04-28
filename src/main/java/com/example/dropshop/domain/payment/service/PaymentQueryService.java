package com.example.dropshop.domain.payment.service;

import com.example.dropshop.common.config.PortOneProperties;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.facade.OrderFacadeService;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.exception.PaymentException;
import com.example.dropshop.domain.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 조회와 PortOne 요청 정보 조회용 서비스를 제공한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentQueryService {

  private final PaymentRepository paymentRepository;
  private final OrderFacadeService orderFacadeService;
  private final PortOneProperties portOneProperties;

  @Transactional(readOnly = true)
  public Payment getPayment(Long paymentId, String email) {
    Payment payment = paymentRepository.findById(paymentId)
        .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
    validateOrderOwnership(payment.getOrderId(), email);
    return payment;
  }

  @Transactional(readOnly = true)
  public Order getOrder(Long orderId, String email) {
    return orderFacadeService.findOrderForPayment(orderId, email);
  }

  public String getStoreId() {
    return portOneProperties.storeId();
  }

  public String getChannelKey() {
    return portOneProperties.channelKey();
  }

  public String getRedirectUrl() {
    return portOneProperties.redirectUrl();
  }

  private void validateOrderOwnership(Long orderId, String email) {
    orderFacadeService.findOrderForPayment(orderId, email);
  }
}
