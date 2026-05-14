package com.example.dropshop.domain.drops.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.exception.ServiceException;
import com.example.dropshop.common.security.SellerAuthContext;
import com.example.dropshop.common.security.SellerAuthResolver;
import com.example.dropshop.domain.auth.service.TokenBlacklistService;
import com.example.dropshop.domain.notification.drops.controller.DropsController;
import com.example.dropshop.domain.notification.drops.dto.request.DropCreateRequest;
import com.example.dropshop.domain.notification.drops.dto.response.DropResponse;
import com.example.dropshop.domain.notification.drops.service.DropsFacadeService;
import com.example.dropshop.domain.notification.drops.service.DropsQueryService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DropsController.class)
class DropsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  @MockitoBean private DropsFacadeService dropsFacadeService;

  @MockitoBean
  // /mine 조회 케이스를 같은 클래스에서 확장할 때 재사용한다.
  private DropsQueryService dropsQueryService;

  @MockitoBean private SellerAuthResolver sellerAuthResolver;

  @MockitoBean private TokenBlacklistService tokenBlacklistService;

  @Test
  @DisplayName("드랍 생성 성공 - @AuthenticationPrincipal email 사용")
  void createDrop_success() throws Exception {
    LocalDateTime startAt = LocalDateTime.of(2026, 5, 1, 10, 0, 0);
    LocalDateTime endAt = LocalDateTime.of(2026, 5, 1, 11, 0, 0);

    SellerAuthContext sellerAuth = new SellerAuthContext(1L, true, true);
    DropResponse response =
        DropResponse.builder()
            .dropId(10L)
            .productId(100L)
            .status("SCHEDULED")
            .startAt(startAt)
            .endAt(endAt)
            .totalStock(50L)
            .remainStock(50L)
            .purchaseLimit(1L)
            .useQueue(true)
            .createdAt(LocalDateTime.of(2026, 4, 23, 10, 0, 0))
            .modifiedAt(LocalDateTime.of(2026, 4, 23, 10, 0, 0))
            .build();

    given(sellerAuthResolver.resolve(any())).willReturn(sellerAuth);
    given(
            dropsFacadeService.createSellerDrop(
                eq(1L), eq(true), eq(true), any(DropCreateRequest.class)))
        .willReturn(response);

    String request =
        """
        {
          "productId": 100,
          "startAt": "2026-05-01T10:00:00",
          "endAt": "2026-05-01T11:00:00",
          "totalStock": 50,
          "purchaseLimit": 1,
          "useQueue": true
        }
        """;

    mockMvc
        .perform(
            post("/api/sellers/drops").with(csrf()).contentType(APPLICATION_JSON).content(request))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.code").value(201))
        .andExpect(jsonPath("$.data.dropId").value(10L))
        .andExpect(jsonPath("$.data.productId").value(100L))
        .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
        .andExpect(jsonPath("$.data.startAt").value("2026-05-01T10:00:00"))
        .andExpect(jsonPath("$.data.endAt").value("2026-05-01T11:00:00"))
        .andExpect(jsonPath("$.data.totalStock").value(50))
        .andExpect(jsonPath("$.data.remainStock").value(50))
        .andExpect(jsonPath("$.data.purchaseLimit").value(1))
        .andExpect(jsonPath("$.data.useQueue").value(true));
  }

  @Test
  @DisplayName("드랍 생성 실패 - 유효성 검증 실패 시 400")
  void createDrop_validationFail() throws Exception {
    String invalidRequest =
        """
        {
          "productId": null,
          "startAt": "2026-05-01T10:00:00",
          "endAt": "2026-05-01T11:00:00",
          "totalStock": 50,
          "purchaseLimit": 1,
          "useQueue": true
        }
        """;

    mockMvc
        .perform(
            post("/api/sellers/drops")
                .with(csrf())
                .contentType(APPLICATION_JSON)
                .content(invalidRequest))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.code").value(400))
        .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.data.message").exists());
  }

  @Test
  @DisplayName("드랍 생성 실패 - 미인증 요청")
  void createDrop_unauthorized() throws Exception {
    // 인증 컨텍스트를 해석하지 못한 경우(미인증/권한 없음)를 resolver에서 표현한다.
    given(sellerAuthResolver.resolve(any()))
        .willThrow(new ServiceException(ErrorCode.SELLER_ROLE_REQUIRED));

    String request =
        """
        {
          "productId": 100,
          "startAt": "2026-05-01T10:00:00",
          "endAt": "2026-05-01T11:00:00",
          "totalStock": 50,
          "purchaseLimit": 1,
          "useQueue": true
        }
        """;

    mockMvc
        .perform(
            post("/api/sellers/drops").with(csrf()).contentType(APPLICATION_JSON).content(request))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value(403));

    verifyNoInteractions(dropsFacadeService);
  }

  @Test
  @DisplayName("드랍 생성 실패 - 서비스 예외 응답 구조")
  void createDrop_serviceException() throws Exception {
    SellerAuthContext sellerAuth = new SellerAuthContext(1L, true, true);
    given(sellerAuthResolver.resolve(any())).willReturn(sellerAuth);
    given(
            dropsFacadeService.createSellerDrop(
                eq(1L), eq(true), eq(true), any(DropCreateRequest.class)))
        .willThrow(new ServiceException(ErrorCode.DROP_ALREADY_EXISTS));

    String request =
        """
        {
          "productId": 100,
          "startAt": "2026-05-01T10:00:00",
          "endAt": "2026-05-01T11:00:00",
          "totalStock": 50,
          "purchaseLimit": 1,
          "useQueue": true
        }
        """;

    mockMvc
        .perform(
            post("/api/sellers/drops").with(csrf()).contentType(APPLICATION_JSON).content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value(400))
        .andExpect(jsonPath("$.message").value(ErrorCode.DROP_ALREADY_EXISTS.getMessage()));

    verify(dropsFacadeService)
        .createSellerDrop(eq(1L), eq(true), eq(true), any(DropCreateRequest.class));
  }
}
