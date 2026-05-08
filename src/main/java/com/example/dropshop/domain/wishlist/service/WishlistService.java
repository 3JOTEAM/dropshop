package com.example.dropshop.domain.wishlist.service;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.exception.ServiceException;
import com.example.dropshop.domain.wishlist.dto.WishlistDto;
import com.example.dropshop.domain.wishlist.dto.request.WishlistRequest;
import com.example.dropshop.domain.wishlist.dto.response.WishlistResponse;
import com.example.dropshop.domain.wishlist.entity.Wishlist;
import com.example.dropshop.domain.wishlist.repository.WishlistRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 찜 서비스. */
@Service
@RequiredArgsConstructor
@Slf4j
public class WishlistService {

  private final RedisTemplate<String, Long> redisTemplate;
  private final WishlistRepository wishlistRepository;

  private static final String WISHLIST_USER_KEY = "wishlist:user:";

  private String key(Long userId) {
    return WISHLIST_USER_KEY + userId;
  }

  /**
   * 찜 생성.
   *
   * @param request 요청.
   * @return 리턴.
   */
  @Transactional
  public WishlistResponse create(Long userId, WishlistRequest request) {
    String key = key(userId);
    Long dropId = request.getDropId();

    if (wishlistRepository.existsByUserIdAndDropId(userId, dropId)) {
      throw new ServiceException(ErrorCode.ALEADY_WISHLIST);
    }

    Wishlist saved = wishlistRepository.saveAndFlush(new Wishlist(userId, dropId));

    // Redis는 캐시 → 실패해도 무시
    try {
      double score = System.currentTimeMillis();
      redisTemplate.opsForZSet().add(key, dropId, score);
    } catch (Exception e) {
      log.warn("Redis 저장 실패 (create) - fallback to DB", e);
    }

    return WishlistResponse.build(dropId, saved.getCreatedAt());
  }

  /**
   * 찜 취소.
   *
   * @param request 요청.
   */
  @Transactional
  public void cancel(Long userId, WishlistRequest request) {
    String key = key(userId);
    Long dropId = request.getDropId();

    if (!wishlistRepository.existsByDropId(dropId)) {
      throw new ServiceException(ErrorCode.DROP_NOT_FOUND);
    }

    wishlistRepository.deleteByUserIdAndDropId(userId, dropId);

    // Redis는 캐시 → 실패해도 무시
    try {
      redisTemplate.opsForZSet().remove(key, dropId);
    } catch (Exception e) {
      log.warn("Redis 삭제 실패 (cancel) - fallback to DB", e);
    }
  }

  /**
   * 최근 찜 목록 가져오기.
   *
   * @param size 목록 사이즈.
   * @return 리턴.
   */
  @Transactional(readOnly = true)
  public List<WishlistResponse> getRecent(Long userId, int size) {
    String key = key(userId);

    // Redis 조회 시도
    try {
      Set<ZSetOperations.TypedTuple<Long>> result =
          redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, size - 1);

      if (result != null && !result.isEmpty()) {
        return result.stream()
            .map(
                tuple -> {
                  Long dropId = ((Number) tuple.getValue()).longValue();
                  Double score = tuple.getScore();

                  LocalDateTime createdAt =
                      Instant.ofEpochMilli(score.longValue())
                          .atZone(ZoneId.systemDefault())
                          .toLocalDateTime();

                  return WishlistResponse.build(dropId, createdAt);
                })
            .toList();
      }
    } catch (Exception e) {
      log.warn("Redis 조회 실패 (getRecent) - fallback to DB", e);
    }

    // Redis miss or 장애 -> DB 조회
    List<Wishlist> list = wishlistRepository.findByUserIdOrderByCreatedAtDesc(userId,
        PageRequest.of(0, size));

    List<WishlistResponse> response =
        list.stream().map(w -> WishlistResponse.build(w.getDropId(), w.getCreatedAt())).toList();

    // Redis 재적재 (lazy caching)
    try {
      for (Wishlist w : list) {
        double score = w.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        redisTemplate.opsForZSet().add(key, w.getDropId(), score);
      }
    } catch (Exception e) {
      log.warn("Redis 재적재 실패 (getRecent)", e);
    }

    return response;
  }
}
