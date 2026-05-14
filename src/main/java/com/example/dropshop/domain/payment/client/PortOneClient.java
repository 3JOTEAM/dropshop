package com.example.dropshop.domain.payment.client;

import com.example.dropshop.common.config.PortOneProperties;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.payment.dto.response.PortOnePaymentResponse;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.exception.PaymentException;
import com.example.dropshop.domain.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** PortOne REST API와 통신하는 클라이언트다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PortOneClient {

  private final PortOneProperties portOneProperties;
  private final PaymentRepository paymentRepository;

  /**
   * PortOne에서 결제 정보를 단건 조회한다.
   *
   * @param paymentId PortOne 결제 식별자
   * @return PortOne 결제 응답
   * @throws PaymentException API Secret이 없거나 외부 API 호출에 실패한 경우
   */
  public PortOnePaymentResponse getPayment(String paymentId) {
    if (portOneProperties.mock()) {
      log.info("[PortOneClient] Mock 모드 - 결제 성공 응답 반환: paymentId={}", paymentId);
      Payment payment =
          paymentRepository
              .findByMerchantPaymentId(paymentId)
              .orElseThrow(() -> new PaymentException(ErrorCode.PAYMENT_NOT_FOUND));
      return new PortOnePaymentResponse(
          paymentId,
          "PAID",
          "mock-txn-" + paymentId,
          new PortOnePaymentResponse.Amount(payment.getAmount()));
    }
    try {
      return restClient()
          .get()
          .uri("/payments/{paymentId}", paymentId)
          .retrieve()
          .body(PortOnePaymentResponse.class);
    } catch (RestClientException e) {
      throw new PaymentException(ErrorCode.PAYMENT_PORTONE_API_ERROR, e.getMessage());
    }
  }

  /**
   * PortOne에 결제 취소(환불)를 요청한다.
   *
   * @param paymentId PortOne 결제 식별자
   * @param amount 취소 금액
   * @param reason 취소 사유
   * @throws PaymentException 외부 API 호출에 실패한 경우
   */
  public void cancelPayment(String paymentId, BigDecimal amount, String reason) {
    if (portOneProperties.mock()) {
      log.info("[PortOneClient] Mock 모드 - 결제 취소 성공 처리: paymentId={}", paymentId);
      return;
    }
    try {
      restClient()
          .post()
          .uri("/payments/{paymentId}/cancel", paymentId)
          .body(new CancelPaymentBody(portOneProperties.storeId(), toPortOneAmount(amount), reason))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw new PaymentException(ErrorCode.PAYMENT_PORTONE_API_ERROR, e.getMessage());
    }
  }

  private Long toPortOneAmount(BigDecimal amount) {
    try {
      return amount.stripTrailingZeros().longValueExact();
    } catch (ArithmeticException e) {
      throw new PaymentException(ErrorCode.PAYMENT_PORTONE_API_ERROR, "포트원 취소 금액은 정수여야 합니다.");
    }
  }

  private RestClient restClient() {
    return RestClient.builder()
        .baseUrl(portOneProperties.resolvedApiBaseUrl())
        .defaultHeader(HttpHeaders.AUTHORIZATION, "PortOne " + portOneProperties.apiSecret())
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }

  private record CancelPaymentBody(String storeId, Long amount, String reason) {}
}
