package com.example.dropshop.domain.payment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.dropshop.domain.order.enums.OrderStatus;
import com.example.dropshop.domain.payment.dto.request.PaymentConfirmRequest;
import com.example.dropshop.domain.payment.dto.request.PaymentPrepareRequest;
import com.example.dropshop.domain.payment.dto.request.PaymentWebhookRequest;
import com.example.dropshop.domain.payment.dto.response.PaymentConfirmResponse;
import com.example.dropshop.domain.payment.dto.response.PaymentPortOneRequestResponse;
import com.example.dropshop.domain.payment.dto.response.PaymentPrepareResponse;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentMethod;
import com.example.dropshop.domain.payment.facade.PaymentFacadeService;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  @MockitoBean
  private PaymentFacadeService paymentFacadeService;

  @Test
  @DisplayName("결제 준비 성공")
  @WithMockUser(username = "test@test.com")
  void preparePayment_success() throws Exception {
    PaymentPrepareRequest request = new PaymentPrepareRequest();
    ReflectionTestUtils.setField(request, "orderId", 1L);
    ReflectionTestUtils.setField(request, "amount", java.math.BigDecimal.valueOf(79000));
    ReflectionTestUtils.setField(request, "idempotencyKey", "payment-test-123");
    ReflectionTestUtils.setField(request, "paymentMethod", PaymentMethod.CARD);

    Payment payment = createPayment();
    PaymentPrepareResponse response = PaymentPrepareResponse.from(payment);

    given(paymentFacadeService.preparePayment(eq("test@test.com"), any(PaymentPrepareRequest.class)))
        .willReturn(response);

    mockMvc.perform(post("/api/payments/prepare")
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.paymentId").value(1L))
        .andExpect(jsonPath("$.data.orderId").value(1L))
        .andExpect(jsonPath("$.data.idempotencyKey").value("payment-test-123"))
        .andExpect(jsonPath("$.data.status").value("PENDING"));
  }

  @Test
  @DisplayName("PortOne 결제 요청 정보 조회 성공")
  @WithMockUser(username = "test@test.com")
  void getPortOneRequest_success() throws Exception {
    Payment payment = createPayment();
    PaymentPortOneRequestResponse response = PaymentPortOneRequestResponse.of(
        payment,
        "store-test",
        "channel-test",
        "ORDER-TEST",
        "http://localhost:3000/payments/redirect"
    );

    given(paymentFacadeService.getPortOneRequest(1L, "test@test.com")).willReturn(response);

    mockMvc.perform(get("/api/payments/{paymentId}/portone-request", 1L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.paymentId").value(1L))
        .andExpect(jsonPath("$.data.storeId").value("store-test"))
        .andExpect(jsonPath("$.data.channelKey").value("channel-test"))
        .andExpect(jsonPath("$.data.portOnePaymentId").value("payment-test-123"))
        .andExpect(jsonPath("$.data.payMethod").value("CARD"));
  }

  @Test
  @DisplayName("결제 확정 성공")
  @WithMockUser(username = "test@test.com")
  void confirmPayment_success() throws Exception {
    PaymentConfirmRequest request = new PaymentConfirmRequest();
    ReflectionTestUtils.setField(request, "portOnePaymentId", "payment-test-123");

    Payment payment = createPayment();
    payment.complete("tx-123");
    PaymentConfirmResponse response = PaymentConfirmResponse.of(payment, OrderStatus.PAID);

    given(paymentFacadeService.confirmPayment(eq(1L), eq("test@test.com"), any(PaymentConfirmRequest.class)))
        .willReturn(response);

    mockMvc.perform(post("/api/payments/{paymentId}/confirm", 1L)
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.paymentId").value(1L))
        .andExpect(jsonPath("$.data.transactionId").value("tx-123"))
        .andExpect(jsonPath("$.data.paymentStatus").value("COMPLETED"))
        .andExpect(jsonPath("$.data.orderStatus").value("PAID"));
  }

  @Test
  @DisplayName("PortOne 웹훅 처리 성공")
  void handleWebhook_success() throws Exception {
    PaymentWebhookRequest request = new PaymentWebhookRequest();
    ReflectionTestUtils.setField(request, "id", "payment-test-123");

    mockMvc.perform(post("/api/payments/webhook")
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("PortOne 웹훅 처리 성공 - data.paymentId 형태도 허용")
  void handleWebhook_dataPaymentId_success() throws Exception {
    PaymentWebhookRequest request = new PaymentWebhookRequest();
    ReflectionTestUtils.setField(request, "data", Map.of("paymentId", "payment-test-123"));

    mockMvc.perform(post("/api/payments/webhook")
            .contentType(APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  @DisplayName("결제 준비 실패 - 필수값이 없으면 400을 반환한다")
  @WithMockUser(username = "test@test.com")
  void preparePayment_validationFail() throws Exception {
    mockMvc.perform(post("/api/payments/prepare")
            .contentType(APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  private Payment createPayment() {
    Payment payment = Payment.prepare(
        1L,
        "payment-test-123",
        PaymentMethod.CARD,
        java.math.BigDecimal.valueOf(79000)
    );
    ReflectionTestUtils.setField(payment, "id", 1L);
    return payment;
  }
}
