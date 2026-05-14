package com.example.dropshop.domain.drops.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.dropshop.domain.auth.service.TokenBlacklistService;
import com.example.dropshop.domain.notification.drops.controller.DropsQueryController;
import com.example.dropshop.domain.notification.drops.dto.response.DropListItemResponse;
import com.example.dropshop.domain.notification.drops.dto.response.DropResponse;
import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import com.example.dropshop.domain.notification.drops.service.DropsQueryService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DropsQueryController.class)
class DropsQueryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  @MockitoBean private DropsQueryService dropsQueryService;

  @MockitoBean private TokenBlacklistService tokenBlacklistService;

  @Test
  @DisplayName("공개 드롭 목록 조회 성공")
  void getPublicDrops_success() throws Exception {
    DropListItemResponse item =
        DropListItemResponse.builder()
            .dropId(10L)
            .productId(1L)
            .productName("테스트 상품")
            .thumbnailUrl("https://example.com/thumb.jpg")
            .status("ACTIVE")
            .startAt(LocalDateTime.of(2026, 4, 25, 12, 0))
            .endAt(LocalDateTime.of(2026, 4, 26, 12, 0))
            .soldCount(5L)
            .remainStock(25L)
            .purchaseLimit(1L)
            .useQueue(true)
            .build();

    Page<DropListItemResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);

    given(dropsQueryService.findPublicDrops(eq(DropsStatus.ACTIVE), any())).willReturn(page);

    mockMvc
        .perform(get("/api/drops").param("status", "ACTIVE").param("page", "0").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].dropId").value(10L))
        .andExpect(jsonPath("$.data.content[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$.data.content[0].soldCount").value(5L));
  }

  @Test
  @DisplayName("공개 드롭 상세 조회 성공")
  void getDropDetail_success() throws Exception {
    DropResponse response =
        DropResponse.builder()
            .dropId(10L)
            .productId(1L)
            .status("ACTIVE")
            .startAt(LocalDateTime.of(2026, 4, 25, 12, 0))
            .endAt(LocalDateTime.of(2026, 4, 26, 12, 0))
            .totalStock(30L)
            .remainStock(25L)
            .viewCount(10L)
            .purchaseLimit(1L)
            .useQueue(true)
            .build();

    given(dropsQueryService.findPublicDropDetail(eq(10L), isNull(), any(), any()))
        .willReturn(response);

    mockMvc
        .perform(get("/api/drops/{dropId}", 10L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.dropId").value(10L))
        .andExpect(jsonPath("$.data.viewCount").value(10L))
        .andExpect(jsonPath("$.data.useQueue").value(true));
  }
}
