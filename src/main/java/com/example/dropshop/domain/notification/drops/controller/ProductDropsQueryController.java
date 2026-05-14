package com.example.dropshop.domain.notification.drops.controller;

import com.example.dropshop.common.dto.ApiResponse;
import com.example.dropshop.domain.notification.drops.dto.response.DropListItemResponse;
import com.example.dropshop.domain.notification.drops.service.DropsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상품별 드롭 이력 조회 API 컨트롤러. 기술 설명. - 드롭 조회 책임을 공개 조회 컨트롤러와 분리해 명확한 라우팅 유지 - Page 응답을 공통
 * ApiResponse.PageResponse 형식으로 래핑
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductDropsQueryController {

  private final DropsQueryService dropsQueryService;

  /** 특정 상품의 드롭 이력을 조회한다. */
  @GetMapping("/{productId}/drops")
  public ResponseEntity<ApiResponse<ApiResponse.PageResponse<DropListItemResponse>>>
      getDropsByProduct(
          @PathVariable Long productId,
          @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
              Pageable pageable) {
    Page<DropListItemResponse> response = dropsQueryService.findDropsByProduct(productId, pageable);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }
}
