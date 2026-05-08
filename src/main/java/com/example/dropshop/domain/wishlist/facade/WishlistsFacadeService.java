package com.example.dropshop.domain.wishlist.facade;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.exception.ServiceException;
import com.example.dropshop.domain.user.entity.User;
import com.example.dropshop.domain.user.repository.UserRepository;
import com.example.dropshop.domain.wishlist.dto.request.WishlistRequest;
import com.example.dropshop.domain.wishlist.dto.response.WishlistResponse;
import com.example.dropshop.domain.wishlist.service.WishlistService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 찜 facade 서비스. */
@Service
@RequiredArgsConstructor
public class WishlistsFacadeService {

  private final WishlistService wishlistService;
  private final UserRepository userRepository;

  private Long getUserId(String email) {
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new ServiceException(ErrorCode.USER_NOT_FOUND));

    return user.getId();
  }

  /**
   * 찜 생성.
   *
   * @param email   이메일.
   * @param request 요청.
   * @return 리턴.
   */
  public WishlistResponse create(String email, WishlistRequest request) {
    Long userId = getUserId(email);

    return wishlistService.create(userId, request);
  }

  /**
   * 찜 취소.
   *
   * @param email   이메일.
   * @param request 요청.
   */
  public void cancel(String email, WishlistRequest request) {
    Long userId = getUserId(email);

    wishlistService.cancel(userId, request);
  }

  public List<WishlistResponse> getRecent(String email, int size) {
    Long userId = getUserId(email);

    return wishlistService.getRecent(userId, size);
  }
}
