package com.example.dropshop.domain.drops.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.dropshop.domain.notification.drops.dto.response.DropListItemResponse;
import com.example.dropshop.domain.notification.drops.dto.response.DropResponse;
import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.enums.DropsStatus;
import com.example.dropshop.domain.notification.drops.repository.DropsRepository;
import com.example.dropshop.domain.notification.drops.service.DropsQueryService;
import com.example.dropshop.domain.product.entity.Product;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DropsQueryServiceTest {

  @Mock private DropsRepository dropsRepository;

  @Mock private StringRedisTemplate stringRedisTemplate;

  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private DropsQueryService dropsQueryService;

  @Test
  @DisplayName("공개 드롭 목록 조회 성공")
  void findPublicDrops_success() {
    Product product = createProduct(1L);
    Drops drop = createDrop(product, DropsStatus.ACTIVE);
    Page<Drops> page = new PageImpl<>(List.of(drop), PageRequest.of(0, 20), 1);

    given(dropsRepository.findAllByStatusIn(any(), any(Pageable.class))).willReturn(page);

    Page<DropListItemResponse> result =
        dropsQueryService.findPublicDrops(null, PageRequest.of(0, 20));

    assertThat(result).hasSize(1);
    assertThat(result.getContent().get(0).getDropId()).isEqualTo(10L);
    assertThat(result.getContent().get(0).getStatus()).isEqualTo("ACTIVE");
    assertThat(result.getContent().get(0).getSoldCount()).isEqualTo(0L);
  }

  @Test
  @DisplayName("판매자 본인 드롭 목록 조회 성공")
  void findSellerDrops_success() {
    Product product = createProduct(1L);
    Drops drop = createDrop(product, DropsStatus.SCHEDULED);
    Page<Drops> page = new PageImpl<>(List.of(drop), PageRequest.of(0, 20), 1);

    given(dropsRepository.findSellerDropsBySellerId(eq(1L), any(Pageable.class))).willReturn(page);

    Page<DropListItemResponse> result =
        dropsQueryService.findSellerDrops(1L, PageRequest.of(0, 20));

    assertThat(result).hasSize(1);
    assertThat(result.getContent().get(0).getProductName()).isEqualTo("테스트 상품");
  }

  @Test
  @DisplayName("상품별 드롭 이력 조회 성공")
  void findDropsByProduct_success() {
    Product product = createProduct(1L);
    Drops drop = createDrop(product, DropsStatus.FINISHED);
    Page<Drops> page = new PageImpl<>(List.of(drop), PageRequest.of(0, 20), 1);

    given(dropsRepository.findAllByProductIdAndStatusIn(eq(1L), any(), any(Pageable.class)))
        .willReturn(page);

    Page<DropListItemResponse> result =
        dropsQueryService.findDropsByProduct(1L, PageRequest.of(0, 20));

    assertThat(result).hasSize(1);
    assertThat(result.getContent().get(0).getStatus()).isEqualTo("FINISHED");
  }

  @Test
  @DisplayName("드롭 상세 조회 시 TTL 내 중복 조회가 아니면 조회수가 1 증가한다")
  void findPublicDropDetail_increaseViewCountOnFirstView() {
    Product product = createProduct(1L);
    Drops drop = createDrop(product, DropsStatus.ACTIVE);

    ReflectionTestUtils.setField(dropsQueryService, "viewCountTtlSeconds", 600L);

    given(dropsRepository.findOneByIdAndStatusIn(eq(10L), any())).willReturn(Optional.of(drop));
    given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(true);
    given(dropsRepository.incrementViewCount(10L)).willReturn(1);

    DropResponse result =
        dropsQueryService.findPublicDropDetail(10L, "user@test.com", "127.0.0.1", "Mozilla/5.0");

    assertThat(result.getViewCount()).isEqualTo(1L);
    verify(dropsRepository).incrementViewCount(10L);
    verify(stringRedisTemplate, never()).delete(anyString());
  }

  @Test
  @DisplayName("드롭 상세 조회 시 TTL 내 중복 조회면 조회수가 증가하지 않는다")
  void findPublicDropDetail_skipViewCountOnDuplicateView() {
    Product product = createProduct(1L);
    Drops drop = createDrop(product, DropsStatus.ACTIVE);

    ReflectionTestUtils.setField(dropsQueryService, "viewCountTtlSeconds", 600L);

    given(dropsRepository.findOneByIdAndStatusIn(eq(10L), any())).willReturn(Optional.of(drop));
    given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(false);

    DropResponse result =
        dropsQueryService.findPublicDropDetail(10L, "user@test.com", "127.0.0.1", "Mozilla/5.0");

    assertThat(result.getViewCount()).isEqualTo(0L);
    verify(dropsRepository, never()).incrementViewCount(10L);
    verify(stringRedisTemplate, never()).delete(anyString());
  }

  @Test
  @DisplayName("조회수 DB 증가가 실패하면 선점한 Redis 키를 롤백한다")
  void findPublicDropDetail_rollbackViewKeyWhenIncrementUpdatedZero() {
    Product product = createProduct(1L);
    Drops drop = createDrop(product, DropsStatus.ACTIVE);

    ReflectionTestUtils.setField(dropsQueryService, "viewCountTtlSeconds", 600L);

    given(dropsRepository.findOneByIdAndStatusIn(eq(10L), any())).willReturn(Optional.of(drop));
    given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(true);
    given(dropsRepository.incrementViewCount(10L)).willReturn(0);

    DropResponse result =
        dropsQueryService.findPublicDropDetail(10L, "user@test.com", "127.0.0.1", "Mozilla/5.0");

    assertThat(result.getViewCount()).isEqualTo(0L);
    verify(stringRedisTemplate).delete(anyString());
  }

  @Test
  @DisplayName("조회수 DB 증가 중 예외가 발생하면 선점한 Redis 키를 롤백한다")
  void findPublicDropDetail_rollbackViewKeyWhenIncrementThrows() {
    Product product = createProduct(1L);
    Drops drop = createDrop(product, DropsStatus.ACTIVE);

    ReflectionTestUtils.setField(dropsQueryService, "viewCountTtlSeconds", 600L);

    given(dropsRepository.findOneByIdAndStatusIn(eq(10L), any())).willReturn(Optional.of(drop));
    given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(true);
    given(dropsRepository.incrementViewCount(10L)).willThrow(new RuntimeException("db failure"));

    DropResponse result =
        dropsQueryService.findPublicDropDetail(10L, "user@test.com", "127.0.0.1", "Mozilla/5.0");

    assertThat(result.getViewCount()).isEqualTo(0L);
    verify(stringRedisTemplate).delete(anyString());
  }

  @Test
  @DisplayName("비로그인 사용자 조회수 증가 - IP+UA 해시로 식별")
  void findPublicDropDetail_increaseViewCountForGuestUser() {
    Product product = createProduct(1L);
    Drops drop = createDrop(product, DropsStatus.ACTIVE);

    ReflectionTestUtils.setField(dropsQueryService, "viewCountTtlSeconds", 600L);

    given(dropsRepository.findOneByIdAndStatusIn(eq(10L), any())).willReturn(Optional.of(drop));
    given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(true);
    given(dropsRepository.incrementViewCount(10L)).willReturn(1);

    DropResponse result =
        dropsQueryService.findPublicDropDetail(10L, null, "192.168.1.100", "Chrome/User-Agent");

    assertThat(result.getViewCount()).isEqualTo(1L);
    verify(dropsRepository).incrementViewCount(10L);

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations).setIfAbsent(keyCaptor.capture(), eq("1"), any(Duration.class));
    assertThat(keyCaptor.getValue()).startsWith("drop:view:10:");
    assertThat(keyCaptor.getValue()).doesNotContain("+").doesNotContain("/").doesNotContain("=");
  }

  @Test
  @DisplayName("비로그인 사용자 키는 IP가 다르면 다르게 생성된다")
  void findPublicDropDetail_differentGuestUserIncreasesViewCount() {
    Product product = createProduct(1L);
    Drops drop = createDrop(product, DropsStatus.ACTIVE);

    ReflectionTestUtils.setField(dropsQueryService, "viewCountTtlSeconds", 600L);

    given(dropsRepository.findOneByIdAndStatusIn(eq(10L), any())).willReturn(Optional.of(drop));
    given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.setIfAbsent(anyString(), eq("1"), any(Duration.class))).willReturn(true);
    given(dropsRepository.incrementViewCount(10L)).willReturn(1);

    dropsQueryService.findPublicDropDetail(10L, null, "203.0.113.50", "Safari/User-Agent");
    dropsQueryService.findPublicDropDetail(10L, null, "203.0.113.51", "Safari/User-Agent");

    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(valueOperations, times(2))
        .setIfAbsent(keyCaptor.capture(), eq("1"), any(Duration.class));
    assertThat(keyCaptor.getAllValues().get(0)).isNotEqualTo(keyCaptor.getAllValues().get(1));
    assertThat(keyCaptor.getAllValues().get(0)).startsWith("drop:view:10:");
    assertThat(keyCaptor.getAllValues().get(1)).startsWith("drop:view:10:");
  }

  private Product createProduct(Long sellerId) {
    Product product =
        Product.create(
            sellerId,
            "테스트 상품",
            "TEST",
            new BigDecimal("100000"),
            10,
            100,
            "https://example.com/thumb.jpg",
            "상품 설명",
            "상품 상세",
            "배송 안내",
            "환불 정책");
    ReflectionTestUtils.setField(product, "id", 1L);
    return product;
  }

  private Drops createDrop(Product product, DropsStatus status) {
    Drops drops =
        Drops.create(
            product,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now().plusDays(2),
            30L,
            1L,
            true);
    ReflectionTestUtils.setField(drops, "id", 10L);
    ReflectionTestUtils.setField(drops, "status", status);
    ReflectionTestUtils.setField(drops, "viewCount", 0L);
    return drops;
  }
}
