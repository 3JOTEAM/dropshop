package com.example.dropshop.domain.recommendation.dto.response;

import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 상품 추천 응답 DTO. */
@Getter
@Builder(access = AccessLevel.PRIVATE)
public class RecommendationResponse {

  /** 사용자가 입력한 질의 */
  private final String query;

  /** GPT가 생성한 추천 문구 */
  private final String recommendation;

  /** 추천된 상품 ID 목록 */
  private final List<Long> productIds;

  public static RecommendationResponse of(
      String query, String recommendation, List<Long> productIds) {
    return RecommendationResponse.builder()
        .query(query)
        .recommendation(recommendation)
        .productIds(productIds)
        .build();
  }

  public static RecommendationResponse empty(String query) {
    return RecommendationResponse.builder()
        .query(query)
        .recommendation("현재 추천할 상품이 없습니다. 더 많은 상품이 등록되면 추천이 가능합니다.")
        .productIds(List.of())
        .build();
  }

  public static RecommendationResponse fallback(String query) {
    return RecommendationResponse.builder()
        .query(query)
        .recommendation("현재 추천 서비스를 일시적으로 이용할 수 없습니다. 잠시 후 다시 시도해 주세요.")
        .productIds(List.of())
        .build();
  }
}
