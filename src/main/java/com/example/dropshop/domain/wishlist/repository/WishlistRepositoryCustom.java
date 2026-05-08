package com.example.dropshop.domain.wishlist.repository;

import com.example.dropshop.domain.wishlist.dto.response.WishlistResponse;
import java.util.List;

/** 찜 커스텀 리포지토리. */
public interface WishlistRepositoryCustom {

  List<WishlistResponse> findRecentByUserId(Long userId, int limit);
}
