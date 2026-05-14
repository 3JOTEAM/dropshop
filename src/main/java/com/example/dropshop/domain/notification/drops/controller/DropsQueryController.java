package com.example.dropshop.domain.notification.drops.controller;

import com.example.dropshop.common.dto.ApiResponse;
import com.example.dropshop.domain.notification.drops.dto.response.DropListItemResponse;
import com.example.dropshop.domain.notification.drops.dto.response.DropResponse;
import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import com.example.dropshop.domain.notification.drops.service.DropsQueryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 드롭 조회 API 컨트롤러. 기술: - CQRS 패턴: Command(생성/수정/삭제)와 Query(조회) 분리 - 읽기 전용 트랜잭션으로 성능 최적화
 * - @PageableDefault: 기본 페이징 설정
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/drops")
public class DropsQueryController {

  private final DropsQueryService dropsQueryService;

  /** 공개 드롭 목록 조회. - SCHEDULED(예정), ACTIVE(진행), FINISHED(종료) 상태 드롭 조회 - 페이징 지원 - 기본 정렬: 생성일시 역순 */
  @GetMapping
  public ResponseEntity<ApiResponse<ApiResponse.PageResponse<DropListItemResponse>>> getPublicDrops(
      @RequestParam(required = false) DropsStatus status,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    Page<DropListItemResponse> response = dropsQueryService.findPublicDrops(status, pageable);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  /** 드롭 상세 조회. - 공개 상태 드롭만 조회 가능 - 선착순 여부(useQueue) 포함 - 현재 잔여 재고 표시 */
  @GetMapping("/{dropId}")
  public ResponseEntity<ApiResponse<DropResponse>> getDropDetail(
      @AuthenticationPrincipal String userEmail,
      @PathVariable Long dropId,
      HttpServletRequest request) {
    String clientIp = extractClientIp(request);
    String userAgent = request.getHeader("User-Agent");
    DropResponse response =
        dropsQueryService.findPublicDropDetail(dropId, userEmail, clientIp, userAgent);
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  private String extractClientIp(HttpServletRequest request) {
    // 조회수 식별은 위조 가능한 헤더보다 서버가 인지한 원격 주소를 우선 사용한다.
    return request.getRemoteAddr();
  }
}
