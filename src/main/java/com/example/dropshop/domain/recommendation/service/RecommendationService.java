package com.example.dropshop.domain.recommendation.service;

import com.example.dropshop.domain.recommendation.client.OpenAiClient;
import com.example.dropshop.domain.recommendation.client.PineconeClient;
import com.example.dropshop.domain.recommendation.dto.response.RecommendationResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * RAG 기반 상품 추천 서비스.
 *
 * <p>흐름:
 *
 * <ol>
 *   <li>사용자 질의 → OpenAI 임베딩 변환
 *   <li>Pinecone에서 유사 상품 검색 (RAG)
 *   <li>검색된 상품 정보 → GPT에 컨텍스트로 전달
 *   <li>GPT 추천 문구 → 사용자 반환
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "recommendation", name = "enabled", havingValue = "true")
public class RecommendationService {

  private static final int TOP_K = 5;

  private final OpenAiClient openAiClient;
  private final PineconeClient pineconeClient;

  /**
   * 사용자 질의에 맞는 상품을 추천한다.
   *
   * @param query 사용자 질의 (예: "여름에 입기 좋은 캐주얼 상품")
   * @return 추천 결과
   */
  @SuppressWarnings("unchecked")
  public RecommendationResponse recommend(String query) {
    try {
      // 1. 사용자 질의 임베딩
      List<Float> queryVector = openAiClient.embed(query);

      // 2. Pinecone 유사 상품 검색
      List<Map<String, Object>> matches = pineconeClient.query(queryVector, TOP_K);

      if (matches.isEmpty()) {
        return RecommendationResponse.empty(query);
      }

      // 3. 검색된 상품 정보 추출
      List<String> productInfos =
          matches.stream()
              .map(
                  match -> {
                    Map<String, Object> metadata = (Map<String, Object>) match.get("metadata");
                    if (metadata == null) {
                      return "";
                    }
                    return "- %s (%s): %s"
                        .formatted(
                            metadata.getOrDefault("name", ""),
                            metadata.getOrDefault("category", ""),
                            metadata.getOrDefault("description", ""));
                  })
              .filter(s -> !s.isEmpty())
              .collect(Collectors.toList());

      List<Long> productIds =
          matches.stream()
              .map(
                  match -> {
                    Map<String, Object> metadata = (Map<String, Object>) match.get("metadata");
                    if (metadata == null) {
                      return null;
                    }
                    Object id = metadata.get("productId");
                    if (id instanceof Number n) {
                      return n.longValue();
                    }
                    return null;
                  })
              .filter(id -> id != null)
              .collect(Collectors.toList());

      // 4. GPT 추천 문구 생성
      String recommendation = openAiClient.recommend(query, productInfos);

      return RecommendationResponse.of(query, recommendation, productIds);

    } catch (Exception e) {
      log.error("[Recommendation] 추천 서비스 오류 - query: {}, cause: {}", query, e.getMessage(), e);
      return RecommendationResponse.fallback(query);
    }
  }
}
