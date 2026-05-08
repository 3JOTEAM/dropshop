package com.example.dropshop.domain.auth.sse.service;

import static com.example.dropshop.common.constant.kafka.MagicNumbers.SSE_TIMEOUT_MS;

import com.example.dropshop.common.dto.ApiResponse;
import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.exception.ServiceException;
import com.example.dropshop.domain.auth.sse.repository.EmitterRepository;
import com.example.dropshop.domain.drops.entity.Drops;
import com.example.dropshop.domain.drops.repository.DropsRepository;
import com.example.dropshop.domain.drops.service.DropsFacadeService;
import com.example.dropshop.domain.notification.dto.response.NotificationResponse;
import com.example.dropshop.domain.notification.entity.Notification;
import com.example.dropshop.domain.notification.enums.NotificationType;
import com.example.dropshop.domain.notification.repository.NotificationRepository;
import com.example.dropshop.domain.order.entity.Order;
import com.example.dropshop.domain.product.entity.Product;
import com.example.dropshop.domain.seller.entity.Seller;
import com.example.dropshop.domain.seller.service.SellerFacadeService;
import com.example.dropshop.domain.user.entity.User;
import com.example.dropshop.domain.user.service.UserFacadeService;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE(Server-Sent Events) 연결을 관리하는 서비스.
 *
 * <p>이메일을 키로 SseEmitter를 관리하며, 중복 로그인 시 기존 디바이스에
 * force-logout 이벤트를 전송한다.
 *
 * <p>연결 타임아웃: 30분. 타임아웃/완료/오류 시 자동으로 emitter를 제거한다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SseEmitterService {

  private final EmitterRepository emitterRepository;
  private final NotificationRepository notificationRepository;
  private final DropsRepository dropsRepository;
  private final UserFacadeService userFacadeService;
  private final SellerFacadeService sellerFacadeService;
  private final DropsFacadeService dropsFacadeService;

  /**
   * 클라이언트의 SSE 구독 요청을 처리한다. 기존 연결이 있으면 교체한다.
   *
   * @param email 구독 요청 사용자 이메일
   * @return SseEmitter
   */
  public SseEmitter subscribe(String email, String lastEventId) {
    User user = userFacadeService.findByEmail(email).orElseThrow(
        () -> new ServiceException(ErrorCode.USER_NOT_FOUND)
    );

    String userId = String.valueOf(user.getId());

    String emitterId = userId + "_" + System.currentTimeMillis();
    SseEmitter emitter = emitterRepository.save(emitterId, new SseEmitter(SSE_TIMEOUT_MS));

    emitter.onCompletion(() -> emitterRepository.deleteById(emitterId));
    emitter.onTimeout(() -> emitterRepository.deleteById(emitterId));
    emitter.onError(e -> emitterRepository.deleteById(emitterId));

    if (!lastEventId.isEmpty()) {
      Map<String, Object> events = emitterRepository.findAllEventCacheStartWithByUserId(
          String.valueOf(userId));
      events.entrySet().stream()
          .filter(entry -> lastEventId.compareTo(entry.getKey()) < 0)
          .forEach(entry -> {
            try {
              sendToClient(emitter, entry.getKey(), entry.getValue());
            } catch (BadRequestException e) {
              throw new RuntimeException(e);
            }
          });
    }

    return emitter;
  }

  /**
   * 알림을 전송하기 위한 메서드.
   *
   * @param receiver         수신자.
   * @param notificationType 알림타입.
   * @param message          메시지.
   * @param productId        상품 아이디.
   */
  public void send(User receiver, NotificationType notificationType, String message,
      Long productId) {
    Notification notification = notificationRepository.save(
        Notification.create(receiver.getId(), notificationType, message, productId));
    String userId = String.valueOf(receiver.getId());

    Map<String, SseEmitter> sseEmitters = emitterRepository.findAllEmitterStartWithByUserId(userId);

    sseEmitters.forEach(
        (key, emitter) -> {
          try {
            sendToClient(emitter, key, ApiResponse.ok(NotificationResponse.from(notification)));
          } catch (BadRequestException e) {
            throw new RuntimeException(e);
          }
        }
    );
  }

  private void sendToClient(SseEmitter emitter, String emitterId, Object data)
      throws BadRequestException {
    try {
      emitter.send(
          SseEmitter.event()
              .id(emitterId)
              .data(data)
      );

      emitter.complete();
    } catch (IOException ex) {
      emitterRepository.deleteById(emitterId);

      throw new BadRequestException("전송 실패");
    }
  }

  /**
   * 기존 디바이스에 강제 로그아웃 이벤트를 전송한다. 다른 디바이스에서 같은 계정으로 로그인할 때 호출된다.
   *
   * @param email 강제 로그아웃할 사용자 이메일
   */
  public void sendForceLogout(String email) {
    SseEmitter emitter = emitterRepository.findEmitterByEmail(email);

    if (emitter == null) {
      // 기존 디바이스가 SSE 구독 중이 아닌 경우 (앱 재시작 등) — 정상 케이스
      log.debug("[SSE] force-logout 대상 emitter 없음 - email: {}", email);
      return;
    }

    try {
      emitter.send(SseEmitter.event()
          .name("force-logout")
          .data("다른 기기에서 로그인하여 현재 세션이 종료됩니다."));
      emitter.complete();
      log.info("[SSE] force-logout 이벤트 전송 완료 - email: {}", email);
    } catch (IOException e) {
      log.warn("[SSE] force-logout 이벤트 전송 실패 - email: {}", email, e);
    } finally {
      emitterRepository.deleteByEmail(email);
    }
  }

  /**
   * 드랍 임박 알림 전송 메서드.
   *
   * @param drops   드랍 엔티티.
   * @param message 전송할 메시지.
   */
  public void sendDropNotification(Drops drops, String message) {
    Product product = drops.getProduct();

    Seller seller = sellerFacadeService.findById(product.getSellerId()).orElseThrow(
        () -> new ServiceException(ErrorCode.SELLER_NOT_FOUND)
    );

    send(seller.getUser(), NotificationType.DROP_IMPENDING, message, product.getId());
  }

  /**
   * 구매 성공 알림 전송 메서드.
   *
   * @param product 상품 엔티티.
   * @param message 전송할 메시지.
   */
  public void sendPurchaseSuccessNotification(Product product, String message) {
    Seller seller = sellerFacadeService.findById(product.getSellerId()).orElseThrow(
        () -> new ServiceException(ErrorCode.SELLER_NOT_FOUND)
    );

    send(seller.getUser(), NotificationType.PURCHASE_SUCCESS, message, product.getId());
  }

  /**
   * 구매 실패 알림 전송 메서드.
   *
   * @param product 상품 엔티티.
   * @param message 전송할 메시지.
   */
  public void sendPurchaseFailNotification(Product product, String message) {
    Seller seller = sellerFacadeService.findById(product.getSellerId()).orElseThrow(
        () -> new ServiceException(ErrorCode.SELLER_NOT_FOUND)
    );

    send(seller.getUser(), NotificationType.PURCHASE_FAIL, message, product.getId());
  }

  /**
   * 주문 추가 알림 전송 메서드.
   *
   * @param order   주문 엔티티.
   * @param message 전송할 메시지.
   */
  public void sendOrderAddNotification(Order order, String message) {
    User user = userFacadeService.findById(order.getUserId()).orElseThrow(
        () -> new ServiceException(ErrorCode.USER_NOT_FOUND)
    );

    Drops drops = dropsRepository.findByDropId(order.getDropId()).orElseThrow(
        () -> new ServiceException(ErrorCode.DROP_NOT_FOUND)
    );

    send(user, NotificationType.ORDER_ADD, message, drops.getProduct().getId());
  }

  /**
   * 재고 빔 알림 전송 메서드.
   *
   * @param product 상품 엔티티.
   * @param message 전송할 메시지.
   */
  public void sendStockEmptyNotification(Product product, String message) {
    Seller seller = sellerFacadeService.findById(product.getSellerId()).orElseThrow(
        () -> new ServiceException(ErrorCode.SELLER_NOT_FOUND)
    );

    send(seller.getUser(), NotificationType.STOCK_EMPTY, message, product.getId());
  }
}
