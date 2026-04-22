package com.example.dropshop.domain.payment.facade;

import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.payment.dto.request.PaymentConfirmRequest;
import com.example.dropshop.domain.payment.dto.request.PaymentPrepareRequest;
import com.example.dropshop.domain.payment.dto.request.PaymentWebhookRequest;
import com.example.dropshop.domain.payment.dto.response.PaymentConfirmResponse;
import com.example.dropshop.domain.payment.dto.response.PaymentPortOneRequestResponse;
import com.example.dropshop.domain.payment.dto.response.PaymentPrepareResponse;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 유스케이스에 필요한 응답 조합을 담당하는 파사드 서비스다.
 */
@Service
@RequiredArgsConstructor
public class PaymentFacadeService {

  private final PaymentService paymentService;

  /**
   * 결제를 준비하고 응답 DTO로 변환한다.
   *
   * @param request 결제 준비 요청
   * @return 결제 준비 응답
   */
  public PaymentPrepareResponse preparePayment(String email, PaymentPrepareRequest request) {
    Payment payment = paymentService.preparePayment(
        email,
        request.getOrderId(),
        request.getAmount(),
        request.getIdempotencyKey(),
        request.getPaymentMethod()
    );
    return PaymentPrepareResponse.from(payment);
  }

  /**
   * PortOne 결제 요청에 필요한 정보를 조회한다.
   *
   * @param paymentId 결제 ID
   * @return PortOne 요청 정보 응답
   */
  public PaymentPortOneRequestResponse getPortOneRequest(Long paymentId, String email) {
    Payment payment = paymentService.getPayment(paymentId, email);
    Order order = paymentService.getOrder(payment.getOrderId(), email);

    return PaymentPortOneRequestResponse.of(
        payment,
        paymentService.getStoreId(),
        paymentService.getChannelKey(),
        order.getOrderNumber(),
        paymentService.getRedirectUrl()
    );
  }

  /**
   * 결제를 확정하고 주문 상태를 포함한 응답을 반환한다.
   *
   * @param paymentId 결제 ID
   * @param request 결제 확정 요청
   * @return 결제 확정 응답
   */
  public PaymentConfirmResponse confirmPayment(
      Long paymentId,
      String email,
      PaymentConfirmRequest request
  ) {
    Payment payment = paymentService.confirmPayment(paymentId, email, request.getPortOnePaymentId());
    Order order = paymentService.getOrder(payment.getOrderId(), email);
    return PaymentConfirmResponse.of(payment, order.getStatus());
  }

  /**
   * PortOne 웹훅을 처리한다.
   */
  public void handleWebhook(PaymentWebhookRequest request) {
    paymentService.handleWebhook(request.extractPortOnePaymentId());
  }
}
