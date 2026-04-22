package com.example.dropshop.domain.payment.controller;

import com.example.dropshop.common.dto.ApiResponse;
import com.example.dropshop.domain.payment.dto.request.PaymentConfirmRequest;
import com.example.dropshop.domain.payment.dto.request.PaymentPrepareRequest;
import com.example.dropshop.domain.payment.dto.request.PaymentWebhookRequest;
import com.example.dropshop.domain.payment.dto.response.PaymentConfirmResponse;
import com.example.dropshop.domain.payment.dto.response.PaymentPortOneRequestResponse;
import com.example.dropshop.domain.payment.dto.response.PaymentPrepareResponse;
import com.example.dropshop.domain.payment.facade.PaymentFacadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 결제 준비, 요청 정보 조회, 결제 확정을 처리하는 REST 컨트롤러다.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentFacadeService paymentFacadeService;

  /**
   * 주문 결제를 준비하고 결제 엔티티를 생성한다.
   *
   * @param request 결제 준비 요청
   * @return 생성된 결제 정보 응답
   */
  @PostMapping("/prepare")
  public ResponseEntity<ApiResponse<PaymentPrepareResponse>> preparePayment(
      @RequestBody @Valid PaymentPrepareRequest request) {
    return ResponseEntity.status(201)
        .body(ApiResponse.created(paymentFacadeService.preparePayment(getAuthenticatedEmail(), request)));
  }

  /**
   * 프론트엔드의 PortOne 결제 요청에 필요한 정보를 조회한다.
   *
   * @param paymentId 결제 ID
   * @return PortOne 요청 정보 응답
   */
  @GetMapping("/{paymentId}/portone-request")
  public ResponseEntity<ApiResponse<PaymentPortOneRequestResponse>> getPortOneRequest(
      @PathVariable Long paymentId) {
    return ResponseEntity.ok(ApiResponse.ok(
        paymentFacadeService.getPortOneRequest(paymentId, getAuthenticatedEmail())
    ));
  }

  /**
   * PortOne 결제 결과를 검증한 뒤 결제를 확정한다.
   *
   * @param paymentId 결제 ID
   * @param request 결제 확정 요청
   * @return 결제 확정 결과 응답
   */
  @PostMapping("/{paymentId}/confirm")
  public ResponseEntity<ApiResponse<PaymentConfirmResponse>> confirmPayment(
      @PathVariable Long paymentId,
      @RequestBody @Valid PaymentConfirmRequest request
  ) {
    return ResponseEntity.ok(ApiResponse.ok(
        paymentFacadeService.confirmPayment(paymentId, getAuthenticatedEmail(), request)
    ));
  }

  /**
   * PortOne 웹훅을 수신해 결제 상태를 동기화한다.
     *
   * @param request 웹훅 요청 본문
   * @return 처리 성공 응답
   */
  @PostMapping("/webhook")
  public ResponseEntity<ApiResponse<Void>> handleWebhook(
      @RequestBody PaymentWebhookRequest request
  ) {
    paymentFacadeService.handleWebhook(request);
    return ResponseEntity.ok(ApiResponse.ok());
  }

  private String getAuthenticatedEmail() {
    return SecurityContextHolder.getContext().getAuthentication().getName();
  }
}
