package com.example.dropshop.domain.product.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 상품 도메인 제약 조건 설정.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "dropshop.product")
public class ProductConstraints {

  private int maxImageCount;
  private long purchasableBlockHours;
}



