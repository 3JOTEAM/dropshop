package com.example.dropshop.domain.drops.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.dropshop.domain.auth.service.TokenBlacklistService;
import com.example.dropshop.domain.notification.drops.controller.ProductDropsQueryController;
import com.example.dropshop.domain.notification.drops.dto.response.DropListItemResponse;
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

@WebMvcTest(ProductDropsQueryController.class)
class ProductDropsQueryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  @MockitoBean private DropsQueryService dropsQueryService;

  @MockitoBean private TokenBlacklistService tokenBlacklistService;

  @Test
  @DisplayName("상품별 드롭 이력 조회 성공")
  void getDropsByProduct_success() throws Exception {
    DropListItemResponse item =
        DropListItemResponse.builder()
            .dropId(10L)
            .productId(1L)
            .productName("테스트 상품")
            .thumbnailUrl("https://example.com/thumb.jpg")
            .status("FINISHED")
            .startAt(LocalDateTime.of(2026, 4, 25, 12, 0))
            .endAt(LocalDateTime.of(2026, 4, 26, 12, 0))
            .soldCount(30L)
            .remainStock(0L)
            .purchaseLimit(1L)
            .useQueue(true)
            .build();

    Page<DropListItemResponse> page = new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1);

    given(dropsQueryService.findDropsByProduct(eq(1L), any())).willReturn(page);

    mockMvc
        .perform(get("/api/products/{productId}/drops", 1L).param("page", "0").param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content[0].dropId").value(10L))
        .andExpect(jsonPath("$.data.content[0].soldCount").value(30L));
  }
}
