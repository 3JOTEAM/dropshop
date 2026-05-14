package com.example.dropshop.domain.product.dto.response;

import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.product.entity.Product;
import com.example.dropshop.domain.product.entity.ProductImage;
import com.example.dropshop.domain.user.entity.User;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** 상품 상세 응답 DTO. */
@Getter
@Builder
public class ProductDetailResponse {

  private final Long productId;
  private final String name;
  private final BigDecimal price;
  private final int discountRate;
  private final BigDecimal discountAmount;
  private final BigDecimal salePrice;
  private final int stock;
  private final String category;
  private final String status;
  private final String thumbnailUrl;
  private final String description;
  private final String specification;
  private final String deliveryInfo;
  private final String refundPolicy;
  private final boolean isPurchasable;
  private final SellerInfo seller;
  private final DropInfo latestDrop;
  private final List<ProductImageResponse> images;
  private final LocalDateTime createdAt;
  private final LocalDateTime modifiedAt;

  /** 상세 응답 객체를 생성한다. */
  public static ProductDetailResponse of(
      Product product, Drops latestDrop, User seller, boolean isPurchasable) {
    return ProductDetailResponse.builder()
        .productId(product.getId())
        .name(product.getName())
        .price(product.getPrice())
        .discountRate(product.getDiscountRate())
        .discountAmount(product.getDiscountAmount())
        .salePrice(product.getSalePrice())
        .stock(product.getStock())
        .category(product.getCategory())
        .status(product.getStatus().name())
        .thumbnailUrl(product.getThumbnailUrl())
        .description(product.getDescription())
        .specification(product.getSpecification())
        .deliveryInfo(product.getDeliveryInfo())
        .refundPolicy(product.getRefundPolicy())
        .isPurchasable(isPurchasable)
        .seller(SellerInfo.from(seller, product.getSellerId()))
        .latestDrop(DropInfo.from(latestDrop))
        .images(product.getImages().stream().map(ProductImageResponse::from).toList())
        .createdAt(product.getCreatedAt())
        .modifiedAt(product.getModifiedAt())
        .build();
  }

  /** 판매자 정보 DTO. */
  @Getter
  @Builder
  public static class SellerInfo {

    private final Long sellerId;
    private final String email;
    private final String nickname;

    /** 사용자 엔티티로 판매자 정보를 생성한다. */
    public static SellerInfo from(User user, Long sellerId) {
      if (user == null) {
        return SellerInfo.builder().sellerId(sellerId).email(null).nickname(null).build();
      }
      return SellerInfo.builder()
          .sellerId(user.getId())
          .email(user.getEmail())
          .nickname(user.getNickname())
          .build();
    }
  }

  /** 최신 드랍 정보 DTO. */
  @Getter
  @Builder
  public static class DropInfo {

    private final Long dropId;
    private final String status;
    private final LocalDateTime startAt;
    private final LocalDateTime endAt;
    private final Long totalStock;
    private final Long remainStock;
    private final Long purchaseLimit;
    private final boolean useQueue;

    /** 드랍 엔티티로 최신 드랍 정보를 생성한다. */
    public static DropInfo from(Drops drops) {
      if (drops == null) {
        return null;
      }
      return DropInfo.builder()
          .dropId(drops.getId())
          .status(drops.getStatus().name())
          .startAt(drops.getStartAt())
          .endAt(drops.getEndAt())
          .totalStock(drops.getTotalStock())
          .remainStock(drops.getRemainStock())
          .purchaseLimit(drops.getPurchaseLimit())
          .useQueue(drops.isUseQueue())
          .build();
    }
  }

  /** 상세 이미지 DTO. */
  @Getter
  @Builder
  public static class ProductImageResponse {

    private final Long imageId;
    private final String imageUrl;
    private final int sortOrder;
    private final boolean isThumbnail;

    /** 상품 이미지 엔티티로 상세 이미지 정보를 생성한다. */
    public static ProductImageResponse from(ProductImage image) {
      return ProductImageResponse.builder()
          .imageId(image.getId())
          .imageUrl(image.getImageUrl())
          .sortOrder(image.getSortOrder())
          .isThumbnail(image.isThumbnail())
          .build();
    }
  }
}
