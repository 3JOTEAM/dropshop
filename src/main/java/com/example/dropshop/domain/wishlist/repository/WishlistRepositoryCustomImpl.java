package com.example.dropshop.domain.wishlist.repository;

import static com.example.dropshop.domain.notification.drops.entity.QDrops.drops;
import static com.example.dropshop.domain.wishlist.entity.QWishlist.wishlist;

import com.example.dropshop.domain.wishlist.dto.response.WishlistResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** 찜 커스텀 리포지토리 구현체. */
@RequiredArgsConstructor
public class WishlistRepositoryCustomImpl implements WishlistRepositoryCustom {
  private final JPAQueryFactory queryFactory;

  @Override
  public List<WishlistResponse> findRecentByUserId(Long userId, int limit) {
    return queryFactory
        .select(
            Projections.constructor(WishlistResponse.class, wishlist.dropId, wishlist.createdAt))
        .from(wishlist)
        .leftJoin(drops)
        .on(wishlist.dropId.eq(drops.id))
        .where(wishlist.userId.eq(userId))
        .orderBy(wishlist.createdAt.desc())
        .limit(limit)
        .fetch();
  }
}
