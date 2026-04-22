package com.example.dropshop.common.security;

/**
 * 판매자 인증 컨텍스트.
 */
public record SellerAuthContext(Long sellerId, boolean sellerApproved, boolean sellerVerified) {
}

