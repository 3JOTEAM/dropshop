package com.example.dropshop.domain.wishlist.controller;

import com.example.dropshop.common.dto.ApiResponse;
import com.example.dropshop.domain.wishlist.dto.request.WishlistRequest;
import com.example.dropshop.domain.wishlist.dto.response.WishlistResponse;
import com.example.dropshop.domain.wishlist.service.WishlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/wishlists")
public class WishListController {

  private final WishlistService wishlistService;

  @PostMapping
  public ResponseEntity<ApiResponse<WishlistResponse>> create(
      @RequestBody WishlistRequest request
  ) {
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(wishlistService.create(request)));
  }

  @DeleteMapping
  public ResponseEntity<ApiResponse<WishlistResponse>> cancel(
      @RequestBody WishlistRequest request
  ) {
    wishlistService.cancel(request);

    return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.noContent());
  }
}
