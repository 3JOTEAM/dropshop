package com.example.dropshop.dev;

import com.example.dropshop.common.dto.ApiResponse;
import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import com.example.dropshop.domain.notification.drops.repository.DropsRepository;
import com.example.dropshop.domain.notification.drops.service.DropsStockPreemptionService;
import com.example.dropshop.domain.notification.drops.service.DropsStatusTransitionWorker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로컬 개발/통합 테스트 전용 Admin API.
 *
 * <p>운영(prod) 프로파일에서는 등록되지 않습니다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dev")
@Profile("!prod")
public class DevAdminController {

  private final DropsStatusTransitionWorker dropsStatusTransitionWorker;
  private final DropsStockPreemptionService dropsStockPreemptionService;
  private final DropsRepository dropsRepository;

  /**
   * 드랍을 즉시 ACTIVE 상태로 전환하고 Redis 재고 키를 초기화한다.
   *
   * @param dropId 활성화할 드랍 ID
   */
  @PostMapping("/drops/{dropId}/activate")
  public ResponseEntity<ApiResponse<String>> activateDrop(@PathVariable Long dropId) {
    log.info("[DevAdmin] 드랍 즉시 활성화 요청 - dropId={}", dropId);
    boolean activated = dropsStatusTransitionWorker.transitionScheduledDrop(dropId);
    // ACTIVE 전환 여부와 무관하게 Redis 재고 키를 항상 재적재한다 (테스트 편의용)
    dropsStockPreemptionService.preloadStockKey(dropId);
    if (activated) {
      return ResponseEntity.ok(ApiResponse.ok("드랍 " + dropId + " 활성화 완료 (ACTIVE + Redis 재고 키 세팅)"));
    } else {
      return ResponseEntity.ok(ApiResponse.ok("드랍 " + dropId + " 이미 ACTIVE — Redis 재고 키 재적재 완료"));
    }
  }

  /**
   * 특정 상품의 진행 중/예정 드랍을 모두 FINISHED 처리한다.
   *
   * <p>드랍 생성 시 "이미 진행 중인 드랍 존재" 오류를 해결하기 위해 사용한다.
   *
   * @param productId 초기화할 상품 ID
   */
  @Transactional
  @PostMapping("/products/{productId}/finish-drops")
  public ResponseEntity<ApiResponse<String>> finishDropsByProduct(@PathVariable Long productId) {
    List<Drops> drops = dropsRepository.findAllByProductIdAndStatusIn(
        productId,
        List.of(DropsStatus.ACTIVE, DropsStatus.SCHEDULED),
        org.springframework.data.domain.Pageable.unpaged()
    ).getContent();

    drops.forEach(Drops::finish);
    dropsRepository.saveAll(drops);

    log.info("[DevAdmin] 상품 {} 드랍 {} 건 FINISHED 처리 완료", productId, drops.size());
    return ResponseEntity.ok(ApiResponse.ok("상품 " + productId + " 의 드랍 " + drops.size() + "건 → FINISHED 처리 완료"));
  }
}
