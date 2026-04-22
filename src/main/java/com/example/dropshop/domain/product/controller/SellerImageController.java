package com.example.dropshop.domain.product.controller;

import com.example.dropshop.common.dto.ApiResponse;
import com.example.dropshop.common.security.SellerAuthContext;
import com.example.dropshop.common.security.SellerAuthResolver;
import com.example.dropshop.domain.product.dto.request.PresignedUrlIssueRequest;
import com.example.dropshop.domain.product.dto.response.PresignedUrlIssueResponse;
import com.example.dropshop.domain.product.service.ProductImageUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매자 이미지 업로드 API 컨트롤러.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/sellers/images")
public class SellerImageController {

  private final ProductImageUploadService productImageUploadService;
  private final SellerAuthResolver sellerAuthResolver;

  /**
   * S3 Presigned URL을 발급한다.
   */
  @PostMapping("/presigned-url")
  public ResponseEntity<ApiResponse<PresignedUrlIssueResponse>> issuePresignedUrl(
      Authentication authentication,
      @Valid @RequestBody PresignedUrlIssueRequest request
  ) {
    SellerAuthContext sellerAuth = sellerAuthResolver.resolve(authentication);

    PresignedUrlIssueResponse response = productImageUploadService.issuePresignedUrl(
        sellerAuth.sellerId(),
        request
    );

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.created(response));
  }
}
