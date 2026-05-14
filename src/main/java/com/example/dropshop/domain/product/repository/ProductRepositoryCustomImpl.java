package com.example.dropshop.domain.product.repository;

import static com.example.dropshop.domain.notification.drops.entity.QDrops.drops;
import static com.example.dropshop.domain.product.entity.QProduct.product;

import com.example.dropshop.domain.product.entity.Product;
import com.example.dropshop.domain.product.enums.ProductListSortType;
import com.example.dropshop.domain.product.enums.ProductStatus;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

/** 상품 조회용 Querydsl 커스텀 구현체. */
@RequiredArgsConstructor
public class ProductRepositoryCustomImpl implements ProductRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  @Override
  public Page<Product> findPublicProducts(
      Collection<ProductStatus> statuses,
      ProductListSortType sortType,
      LocalDateTime baseTime,
      Pageable pageable) {
    BooleanExpression predicate = product.status.in(statuses);

    List<Product> content =
        queryFactory
            .selectFrom(product)
            .where(predicate)
            .orderBy(resolveOrderSpecifiers(sortType, baseTime))
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

    return PageableExecutionUtils.getPage(
        content,
        pageable,
        () -> {
          Long total =
              queryFactory.select(product.count()).from(product).where(predicate).fetchOne();
          return total == null ? 0L : total;
        });
  }

  private OrderSpecifier<?>[] resolveOrderSpecifiers(
      ProductListSortType sortType, LocalDateTime baseTime) {
    return switch (sortType) {
      case PRICE_HIGH ->
          new OrderSpecifier<?>[] {product.salePrice.desc(), product.createdAt.desc()};
      case PRICE_LOW -> new OrderSpecifier<?>[] {product.salePrice.asc(), product.createdAt.desc()};
      case DROP_IMMINENT -> resolveDropImminentOrderSpecifiers(baseTime);
      case LATEST -> new OrderSpecifier<?>[] {product.createdAt.desc()};
    };
  }

  private OrderSpecifier<?>[] resolveDropImminentOrderSpecifiers(LocalDateTime baseTime) {
    Expression<LocalDateTime> nextDropStartAt = nextDropStartAt(baseTime);
    NumberExpression<Integer> nullRank =
        Expressions.numberTemplate(
            Integer.class, "case when ({0}) is null then 1 else 0 end", nextDropStartAt);

    return new OrderSpecifier<?>[] {
      nullRank.asc(), new OrderSpecifier<>(Order.ASC, nextDropStartAt), product.createdAt.desc()
    };
  }

  private Expression<LocalDateTime> nextDropStartAt(LocalDateTime baseTime) {
    return JPAExpressions.select(drops.startAt.min())
        .from(drops)
        .where(drops.product.id.eq(product.id), drops.startAt.goe(baseTime));
  }
}
