package com.example.dropshop.domain.drops.controller;

import com.example.dropshop.common.dto.ApiResponse;
import com.example.dropshop.common.security.SellerAuthContext;
import com.example.dropshop.common.security.SellerAuthResolver;
import com.example.dropshop.domain.drops.dto.request.DropCreateRequest;
import com.example.dropshop.domain.drops.dto.request.DropUpdateRequest;
import com.example.dropshop.domain.drops.dto.response.DropListItemResponse;
import com.example.dropshop.domain.drops.dto.response.DropResponse;
import com.example.dropshop.domain.drops.service.DropsFacadeService;
import com.example.dropshop.domain.drops.service.DropsQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 드랍 API 컨트롤러.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sellers/drops")
public class DropsController {

  private final DropsFacadeService dropsFacadeService;
  private final DropsQueryService dropsQueryService;
  private final SellerAuthResolver sellerAuthResolver;

  /**
   * 판매자가 새로운 드랍을 생성한다.
   */
  @PostMapping
  public ResponseEntity<ApiResponse<DropResponse>> createDrop(
      Authentication authentication,
      @Valid @RequestBody DropCreateRequest request
  ) {
    SellerAuthContext sellerAuth = sellerAuthResolver.resolve(authentication);
    DropResponse response = dropsFacadeService.createSellerDrop(
        sellerAuth.sellerId(),
        sellerAuth.sellerApproved(),
        sellerAuth.sellerVerified(),
        request
    );
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
  }

  /**
   * 판매자가 본인 드랍을 수정한다.
   */
  @PatchMapping("/{id}")
  public ResponseEntity<ApiResponse<DropResponse>> updateDrop(
      @PathVariable Long id,
      Authentication authentication,
      @Valid @RequestBody DropUpdateRequest request
  ) {
    SellerAuthContext sellerAuth = sellerAuthResolver.resolve(authentication);
    DropResponse response = dropsFacadeService.updateSellerDrop(
        id,
        sellerAuth.sellerId(),
        sellerAuth.sellerApproved(),
        sellerAuth.sellerVerified(),
        request
    );
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  /**
   * 판매자가 본인 드랍을 삭제한다.
   */
  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteDrop(
      @PathVariable Long id,
      Authentication authentication
  ) {
    SellerAuthContext sellerAuth = sellerAuthResolver.resolve(authentication);
    dropsFacadeService.deleteSellerDrop(
        id,
        sellerAuth.sellerId(),
        sellerAuth.sellerApproved(),
        sellerAuth.sellerVerified()
    );
    return ResponseEntity.ok(ApiResponse.ok());
  }

  /**
   * 판매자가 드랍을 강제 종료한다.
   */
  @PatchMapping("/{id}/stop")
  public ResponseEntity<ApiResponse<DropResponse>> stopDrop(
      @PathVariable Long id,
      Authentication authentication
  ) {
    SellerAuthContext sellerAuth = sellerAuthResolver.resolve(authentication);
    DropResponse response = dropsFacadeService.stopSellerDrop(
        id,
        sellerAuth.sellerId(),
        sellerAuth.sellerApproved(),
        sellerAuth.sellerVerified()
    );
    return ResponseEntity.ok(ApiResponse.ok(response));
  }

  /**
   * 판매자 본인 드롭 목록 조회.
   */
  @GetMapping("/mine")
  public ResponseEntity<
      ApiResponse<ApiResponse.PageResponse<DropListItemResponse>>> getMyDrops(
      Authentication authentication,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
      Pageable pageable
  ) {
    SellerAuthContext sellerAuth = sellerAuthResolver.resolve(authentication);
    Page<DropListItemResponse> response = dropsQueryService.findSellerDrops(
        sellerAuth.sellerId(),
        pageable
    );
    return ResponseEntity.ok(ApiResponse.ok(response));
  }
}
