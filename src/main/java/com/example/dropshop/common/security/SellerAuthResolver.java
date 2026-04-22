package com.example.dropshop.common.security;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.exception.ServiceException;
import com.example.dropshop.domain.seller.entity.Seller;
import com.example.dropshop.domain.seller.enums.SellerStatus;
import com.example.dropshop.domain.seller.service.SellerFacadeService;
import com.example.dropshop.domain.user.entity.User;
import com.example.dropshop.domain.user.enums.UserRole;
import com.example.dropshop.domain.user.service.UserFacadeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 판매자 인증 컨텍스트를 해석한다.
 */
@Component
@RequiredArgsConstructor
public class SellerAuthResolver {

  private static final String ANONYMOUS_USER = "anonymousUser";

  private final UserFacadeService userFacadeService;
  private final SellerFacadeService sellerFacadeService;

  /**
   * JWT 인증 정보를 기반으로 판매자 인증 컨텍스트를 해석한다.
   */
  public SellerAuthContext resolve(Authentication authentication) {
    String email = authentication == null ? null : authentication.getName();
    if (email == null || email.isBlank() || ANONYMOUS_USER.equals(email)) {
      throw new ServiceException(ErrorCode.SELLER_ROLE_REQUIRED);
    }
    return resolveFromJwt(email);
  }

  private SellerAuthContext resolveFromJwt(String email) {
    User user = userFacadeService.findByEmail(email)
        .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND));
    if (user.getRole() != UserRole.SELLER) {
      throw new ServiceException(ErrorCode.SELLER_ROLE_REQUIRED);
    }

    Seller seller = sellerFacadeService.findByUser(user)
        .orElseThrow(() -> new ServiceException(ErrorCode.SELLER_NOT_FOUND));
    boolean sellerApproved = seller.getStatus() == SellerStatus.APPROVED;
    boolean sellerVerified = seller.getAccountInfo() != null
        && !seller.getAccountInfo().isBlank();

    // Product/Drops 도메인의 sellerId는 Seller PK가 아니라 User PK 기준이다.
    return new SellerAuthContext(user.getId(), sellerApproved, sellerVerified);
  }
}


