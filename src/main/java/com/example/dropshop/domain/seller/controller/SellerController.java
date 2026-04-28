package com.example.dropshop.domain.seller.controller;

import com.example.dropshop.domain.seller.dto.request.SellerApplyRequest;
import com.example.dropshop.domain.seller.dto.response.SellerResponse;
import com.example.dropshop.domain.seller.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sellers")
@RequiredArgsConstructor
public class SellerController {

    private final SellerService sellerService;

    @PostMapping("/apply")
    public ResponseEntity<SellerResponse> applySeller(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody SellerApplyRequest request) {
        return ResponseEntity.ok(sellerService.applySeller(email, request));
    }

    @GetMapping("/me/status")
    public ResponseEntity<SellerResponse> getMyStatus(
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(sellerService.getMySellerStatus(email));
    }
}
