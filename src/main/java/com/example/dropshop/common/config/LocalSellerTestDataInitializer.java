package com.example.dropshop.common.config;

import com.example.dropshop.domain.dashboard.service.SellerDashboardRefreshService;
import com.example.dropshop.domain.notification.drops.entity.Drops;
import com.example.dropshop.domain.notification.drops.repository.DropsRepository;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.order.entity.OrderItem;
import com.example.dropshop.domain.order.repository.OrderRepository;
import com.example.dropshop.domain.payment.entity.Payment;
import com.example.dropshop.domain.payment.enums.PaymentMethod;
import com.example.dropshop.domain.payment.repository.PaymentRepository;
import com.example.dropshop.domain.product.entity.Product;
import com.example.dropshop.domain.product.enums.ProductStatus;
import com.example.dropshop.domain.product.repository.ProductRepository;
import com.example.dropshop.domain.seller.entity.Seller;
import com.example.dropshop.domain.seller.repository.SellerRepository;
import com.example.dropshop.domain.user.entity.User;
import com.example.dropshop.domain.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
@RequiredArgsConstructor
@Profile({"default", "local"})
public class LocalSellerTestDataInitializer {

  private static final String TEST_SELLER_EMAIL = "seller-test@dropshop.com";
  private static final String TEST_SELLER_PASSWORD = "Password123!";
  private static final String TEST_SELLER_NICKNAME = "테스트판매자";
  private static final String TEST_BUSINESS_NO = "9876543210";
  private static final String TEST_BRAND_NAME = "Dropshop Test Brand";
  private static final String TEST_BRAND_LOGO = "https://cdn.example.com/test-brand-logo.png";
  private static final String TEST_ACCOUNT_INFO = "국민은행 123-456-789012";
  private static final String TEST_COMPANY_NAME = "드랍샵 테스트 법인";
  private static final String TEST_REPRESENTATIVE_NAME = "홍길동";
  private static final String TEST_PHONE_NUMBER = "010-1234-5678";
  private static final String DASHBOARD_PRODUCT_NAME = "대시보드 테스트 상품";
  private static final String DASHBOARD_BUYER_ONE_EMAIL = "dashboard-buyer-1@dropshop.com";
  private static final String DASHBOARD_BUYER_TWO_EMAIL = "dashboard-buyer-2@dropshop.com";
  private static final String DASHBOARD_BUYER_PASSWORD = "Password123!";

  @Bean
  CommandLineRunner initLocalSellerTestData(
      UserRepository userRepository,
      SellerRepository sellerRepository,
      ProductRepository productRepository,
      DropsRepository dropsRepository,
      OrderRepository orderRepository,
      PaymentRepository paymentRepository,
      SellerDashboardRefreshService sellerDashboardRefreshService,
      PasswordEncoder passwordEncoder) {
    return args ->
        initialize(
            userRepository,
            sellerRepository,
            productRepository,
            dropsRepository,
            orderRepository,
            paymentRepository,
            sellerDashboardRefreshService,
            passwordEncoder);
  }

  public void initialize(
      UserRepository userRepository,
      SellerRepository sellerRepository,
      ProductRepository productRepository,
      DropsRepository dropsRepository,
      OrderRepository orderRepository,
      PaymentRepository paymentRepository,
      SellerDashboardRefreshService sellerDashboardRefreshService,
      PasswordEncoder passwordEncoder) {
    User sellerUser = userRepository.findByEmail(TEST_SELLER_EMAIL).orElse(null);
    if (sellerUser == null) {
      sellerUser =
          userRepository.save(
              User.signup(
                  TEST_SELLER_EMAIL,
                  passwordEncoder.encode(TEST_SELLER_PASSWORD),
                  TEST_SELLER_NICKNAME));
    }

    sellerUser.promoteToSeller();
    sellerUser = userRepository.save(sellerUser);

    Seller seller = sellerRepository.findByUser(sellerUser).orElse(null);
    if (seller == null) {
      seller =
          sellerRepository.save(
              Seller.builder()
                  .user(sellerUser)
                  .businessNo(TEST_BUSINESS_NO)
                  .brandName(TEST_BRAND_NAME)
                  .brandLogo(TEST_BRAND_LOGO)
                  .accountInfo(TEST_ACCOUNT_INFO)
                  .companyName(TEST_COMPANY_NAME)
                  .representativeName(TEST_REPRESENTATIVE_NAME)
                  .phoneNumber(TEST_PHONE_NUMBER)
                  .build());
    }

    seller.approve();
    seller = sellerRepository.save(seller);

    log.info(
        "로컬 판매자 테스트 데이터 준비 완료: email={}, password={}, sellerId={}, status={}",
        TEST_SELLER_EMAIL,
        TEST_SELLER_PASSWORD,
        seller.getId(),
        seller.getStatus());

    prepareDashboardSeedData(
        userRepository,
        productRepository,
        dropsRepository,
        orderRepository,
        paymentRepository,
        sellerDashboardRefreshService,
        passwordEncoder,
        sellerUser);
  }

  private void prepareDashboardSeedData(
      UserRepository userRepository,
      ProductRepository productRepository,
      DropsRepository dropsRepository,
      OrderRepository orderRepository,
      PaymentRepository paymentRepository,
      SellerDashboardRefreshService sellerDashboardRefreshService,
      PasswordEncoder passwordEncoder,
      User sellerUser) {
    Product product =
        Product.create(
            sellerUser.getId(),
            DASHBOARD_PRODUCT_NAME,
            "CLOTHING",
            new BigDecimal("129000"),
            0,
            20,
            "https://dummy-image-dashboard",
            "대시보드 확인용 샘플 상품입니다.",
            "면 100%, FREE",
            "기본 배송 안내",
            "기본 환불 정책");
    product.updateStatusByDrop(ProductStatus.READY);
    Product savedProduct = productRepository.save(product);

    Drops drop =
        Drops.create(
            savedProduct,
            LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(7),
            20L,
            1L,
            false);
    drop.activate();
    Drops savedDrop = dropsRepository.save(drop);

    User buyerOne =
        ensureBuyerUser(userRepository, passwordEncoder, DASHBOARD_BUYER_ONE_EMAIL, "대시보드구매자1");
    User buyerTwo =
        ensureBuyerUser(userRepository, passwordEncoder, DASHBOARD_BUYER_TWO_EMAIL, "대시보드구매자2");

    Order firstOrder =
        createPaidOrder(orderRepository, paymentRepository, buyerOne, savedDrop, savedProduct);
    Order secondOrder =
        createPaidOrder(orderRepository, paymentRepository, buyerTwo, savedDrop, savedProduct);

    sellerDashboardRefreshService.refreshForOrder(firstOrder);
    sellerDashboardRefreshService.refreshForOrder(secondOrder);

    log.info(
        "대시보드 샘플 데이터 생성 완료: sellerEmail={}, productId={}, dropId={}, paidOrders=2, buyers=[{}, {}]",
        TEST_SELLER_EMAIL,
        savedProduct.getId(),
        savedDrop.getId(),
        buyerOne.getEmail(),
        buyerTwo.getEmail());
  }

  private User ensureBuyerUser(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      String email,
      String nickname) {
    return userRepository
        .findByEmail(email)
        .orElseGet(
            () ->
                userRepository.save(
                    User.signup(
                        email, passwordEncoder.encode(DASHBOARD_BUYER_PASSWORD), nickname)));
  }

  private Order createPaidOrder(
      OrderRepository orderRepository,
      PaymentRepository paymentRepository,
      User buyer,
      Drops drop,
      Product product) {
    Order order = Order.create(buyer.getId(), drop.getId());
    order.addOrderItem(
        OrderItem.create(
            order,
            product.getId(),
            product.getPrice(),
            product.getSalePrice(),
            product.getDiscountAmount(),
            product.getThumbnailUrl()));
    order.pay();
    Order savedOrder = orderRepository.saveAndFlush(order);

    Payment payment =
        Payment.prepare(
            savedOrder.getId(),
            "dashboard-seed-" + savedOrder.getId(),
            PaymentMethod.CARD,
            savedOrder.getTotalAmount());
    payment.complete("dashboard-tx-" + savedOrder.getId());
    paymentRepository.saveAndFlush(payment);

    return savedOrder;
  }
}
