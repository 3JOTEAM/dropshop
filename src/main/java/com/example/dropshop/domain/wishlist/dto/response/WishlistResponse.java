package com.example.dropshop.domain.wishlist.dto.response;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 찜 응답 DTO. */
@Getter
@AllArgsConstructor
@Builder(access = AccessLevel.PRIVATE)
public class WishlistResponse {

  private Long dropId;

  private LocalDateTime createdAt;

  /**
   * 스태틱 빌더 메소드.
   *
   * @param dropId 드랍 아이디.
   * @return 리턴.
   */
  public static WishlistResponse build(Long dropId, LocalDateTime time) {
    return WishlistResponse.builder()
        .dropId(dropId)
        .createdAt(time)
        .build();
  }
}
