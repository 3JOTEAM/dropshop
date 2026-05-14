package com.example.dropshop.domain.notification.drops.service;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.domain.notification.drops.dto.response.DropListItemResponse;
import com.example.dropshop.domain.notification.drops.dto.response.DropResponse;
import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import com.example.dropshop.domain.notification.drops.exception.DropsException;
import com.example.dropshop.domain.notification.drops.repository.DropsRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 드랍 조회 서비스 (읽기 최적화). 기술 설명. - @Transactional(readOnly = true): 읽기 전용으로 플러시 모드 NEVER, 스냅샷 비활성화 최적화
 * - @EntityGraph: N+1 쿼리 방지, Product를 함께 로드 - Page<T>: 페이징, 정렬, 총 개수 자동 계산
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DropsQueryService {

  private static final Set<DropsStatus> PUBLIC_VISIBLE_STATUSES =
      DropsConstants.PUBLIC_VISIBLE_STATUSES;
  private static final String DROP_VIEW_KEY_PREFIX = "drop:view";

  private final DropsRepository dropsRepository;
  private final StringRedisTemplate stringRedisTemplate;

  @Value("${dropshop.drops.view-count-ttl-seconds:600}")
  private long viewCountTtlSeconds;

  /**
   * 공개 드롭 목록을 조회한다. 도메인 규칙. - SCHEDULED: 예정 드롭 (진행 전) - ACTIVE: 진행 중 드롭 - FINISHED: 종료된 드롭 기술.
   * - @EntityGraph로 Product 즉시 로드 (N+1 방지) - Page 객체로 페이징/정렬/총 개수 자동 처리
   */
  public Page<DropListItemResponse> findPublicDrops(DropsStatus status, Pageable pageable) {
    Set<DropsStatus> filterStatuses = status == null ? PUBLIC_VISIBLE_STATUSES : EnumSet.of(status);

    Page<Drops> drops = dropsRepository.findAllByStatusIn(filterStatuses, pageable);
    return drops.map(DropListItemResponse::from);
  }

  /**
   * 드롭 상세 조회. 도메인 규칙. - SCHEDULED, ACTIVE, FINISHED만 공개 조회 가능 - useQueue 여부 표시 (규칙 4번: 선착순 드롭 구분)
   */
  @Transactional
  public DropResponse findPublicDropDetail(
      Long dropId, String userEmail, String clientIp, String userAgent) {
    Drops drops =
        dropsRepository
            .findOneByIdAndStatusIn(dropId, PUBLIC_VISIBLE_STATUSES)
            .orElseThrow(() -> new DropsException(ErrorCode.DROP_NOT_FOUND));

    long responseViewCount = drops.getViewCount();
    if (shouldIncreaseViewCount(dropId, userEmail, clientIp, userAgent)) {
      responseViewCount += 1;
    }

    return DropResponse.from(drops, responseViewCount);
  }

  private boolean shouldIncreaseViewCount(
      Long dropId, String userEmail, String clientIp, String userAgent) {
    String viewIdentifier;
    if (userEmail != null && !userEmail.isBlank()) {
      viewIdentifier = userEmail;
    } else if (clientIp != null && !clientIp.isBlank()) {
      viewIdentifier = generateGuestIdentifier(clientIp, userAgent);
    } else {
      return false;
    }

    String viewKey = DROP_VIEW_KEY_PREFIX + ":" + dropId + ":" + viewIdentifier;

    Boolean firstView;
    try {
      firstView =
          stringRedisTemplate
              .opsForValue()
              .setIfAbsent(viewKey, "1", Duration.ofSeconds(viewCountTtlSeconds));
    } catch (Exception e) {
      log.warn("조회수 중복 방지 키 처리 실패. dropId={}, userEmail={}", dropId, userEmail, e);
      return false;
    }

    if (!Boolean.TRUE.equals(firstView)) {
      return false;
    }

    try {
      int updated = dropsRepository.incrementViewCount(dropId);
      if (updated <= 0) {
        rollbackViewKey(viewKey);
        return false;
      }
      return true;
    } catch (Exception e) {
      rollbackViewKey(viewKey);
      log.warn("조회수 증가 처리 실패. dropId={}, userEmail={}", dropId, userEmail, e);
      return false;
    }
  }

  private void rollbackViewKey(String viewKey) {
    try {
      stringRedisTemplate.delete(viewKey);
    } catch (Exception e) {
      log.warn("조회수 키 롤백 실패. viewKey={}", viewKey, e);
    }
  }

  private String generateGuestIdentifier(String clientIp, String userAgent) {
    String combined = clientIp + "|" + (userAgent != null ? userAgent : "");
    MessageDigest md = getSha256Digest();
    byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
  }

  private MessageDigest getSha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm is not available", e);
    }
  }

  /**
   * 판매자 본인 드롭 목록 조회. 도메인 규칙. - 판매자는 본인이 등록한 상품의 드롭만 조회 가능 - 모든 상태(SCHEDULED, ACTIVE, FINISHED)의 드롭
   * 조회 가능
   */
  public Page<DropListItemResponse> findSellerDrops(Long sellerId, Pageable pageable) {
    Page<Drops> drops = dropsRepository.findSellerDropsBySellerId(sellerId, pageable);
    return drops.map(DropListItemResponse::from);
  }

  /** 특정 상품의 드롭 이력 조회. 도메인 규칙. - 공개 상태 드롭만 조회 가능 - 판매된 수량(totalStock - remainStock) 포함 */
  public Page<DropListItemResponse> findDropsByProduct(Long productId, Pageable pageable) {
    Page<Drops> drops =
        dropsRepository.findAllByProductIdAndStatusIn(productId, PUBLIC_VISIBLE_STATUSES, pageable);

    return drops.map(DropListItemResponse::from);
  }
}
