//package com.example.dropshop.common.initializer;
//
//import com.example.dropshop.domain.drops.entity.Drops;
//import com.example.dropshop.domain.drops.repository.DropsRepository;
//import com.example.dropshop.domain.product.entity.Product;
//import com.example.dropshop.domain.product.repository.ProductRepository;
//import com.example.dropshop.domain.queue.entity.Queue;
//import com.example.dropshop.domain.queue.repository.QueueRepository;
//import com.example.dropshop.domain.queue.repository.QueueTokenRepository;
//import com.example.dropshop.domain.queue.service.QueueService;
//import com.example.dropshop.domain.user.entity.User;
//import com.example.dropshop.domain.user.repository.UserRepository;
//import java.math.BigDecimal;
//import java.time.LocalDateTime;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import lombok.RequiredArgsConstructor;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.stereotype.Component;
//
//@Component
//@RequiredArgsConstructor
//public class DataInitializer implements CommandLineRunner {
//
//  private final ProductRepository productRepository;
//  private final DropsRepository dropsRepository;
//  private final QueueRepository queueRepository;
//  private final QueueTokenRepository queueTokenRepository;
//  private final UserRepository userRepository;
//  private final QueueService queueService;
//
//  @Override
//  public void run(String... args) throws Exception {
//    Product product = Product.create(
//        1L,
//        "임시 상품 이름",
//        "임시 카테고리",
//        new BigDecimal("10000"),
//        80,
//        100,
//        "thumbnailUrl",
//        "description",
//        "specification",
//        "deliveryInfo",
//        "refundPolicy"
//    );
//
//    productRepository.save(product);
//
//    Drops drops = Drops.create(
//        product,
//        LocalDateTime.now(),
//        LocalDateTime.now().plusHours(5),
//        100L,
//        1L,
//        true
//    );
//
//    drops.activate();
//
//    dropsRepository.save(drops);
//
//    for (int i = 0; i < 100; i++){
//      User user = User.signup(
//          i + "@email.com",
//          "12345678",
//          "nickname" + i
//      );
//
//      userRepository.save(user);
//    }
//
//    ExecutorService executor = Executors.newFixedThreadPool(100);
//
//    for (int i = 1; i <= 100; i++) {
//      int userId = i;
//
//      executor.submit(() -> {
//        queueService.decideQueue(1L, userId + "@email.com");
//      });
//    }
//
//    executor.shutdown();
//
//
////    ExecutorService executor = Executors.newFixedThreadPool(100);
////
////    CountDownLatch latch = new CountDownLatch(1);
////
////    for (int i = 1; i <= 100; i++) {
////      int userId = i;
////
////      executor.submit(() -> {
////        try {
////          latch.await();
////          queueService.decideQueue(1L, (long) userId);
////        } catch (InterruptedException e) {
////          e.printStackTrace();
////        }
////      });
////    }
////
////    latch.countDown();
////
////    executor.shutdown();
//
////    for (int i = 1; i <= 100; i++){
////      queueService.decideQueue(1L, (long)1L);
//////      Queue queue = new Queue((long)i, 1L);
//////      queueRepository.save(queue);
////    }
//  }
//}
