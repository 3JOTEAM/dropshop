package com.example.dropshop.common.config;

import com.example.dropshop.domain.seller.entity.Seller;
import com.example.dropshop.domain.seller.repository.SellerRepository;
import com.example.dropshop.domain.user.entity.User;
import com.example.dropshop.domain.user.repository.UserRepository;
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

    @Bean
    CommandLineRunner initLocalSellerTestData(
            UserRepository userRepository,
            SellerRepository sellerRepository,
            PasswordEncoder passwordEncoder
    ) {
        return args -> initialize(userRepository, sellerRepository, passwordEncoder);
    }

    public void initialize(
            UserRepository userRepository,
            SellerRepository sellerRepository,
            PasswordEncoder passwordEncoder
    ) {
        User sellerUser = userRepository.findByEmail(TEST_SELLER_EMAIL).orElse(null);
        if (sellerUser == null) {
            sellerUser = userRepository.save(User.signup(
                    TEST_SELLER_EMAIL,
                    passwordEncoder.encode(TEST_SELLER_PASSWORD),
                    TEST_SELLER_NICKNAME
            ));
        }

        sellerUser.promoteToSeller();
        sellerUser = userRepository.save(sellerUser);

        Seller seller = sellerRepository.findByUser(sellerUser).orElse(null);
        if (seller == null) {
            seller = sellerRepository.save(Seller.builder()
                    .user(sellerUser)
                    .businessNo(TEST_BUSINESS_NO)
                    .brandName(TEST_BRAND_NAME)
                    .brandLogo(TEST_BRAND_LOGO)
                    .accountInfo(TEST_ACCOUNT_INFO)
                    .build());
        }

        seller.approve();
        seller = sellerRepository.save(seller);

        log.info(
                "로컬 판매자 테스트 데이터 준비 완료: email={}, password={}, sellerId={}, status={}",
                TEST_SELLER_EMAIL,
                TEST_SELLER_PASSWORD,
                seller.getId(),
                seller.getStatus()
        );
    }
}
