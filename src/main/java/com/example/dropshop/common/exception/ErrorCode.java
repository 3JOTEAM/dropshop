package com.example.dropshop.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 공통 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  /**
   * User
   */
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
  INVALID_PASSWORD_FORMAT(HttpStatus.BAD_REQUEST, "비밀번호는 영문, 숫자, 특수문자를 포함한 8~16자여야 합니다."),
  PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "현재 비밀번호가 일치하지 않습니다."),
  ALREADY_WITHDRAWN(HttpStatus.BAD_REQUEST, "이미 탈퇴 처리된 사용자입니다."),

  /**
   * Seller
   */
  SELLER_NOT_FOUND(HttpStatus.NOT_FOUND, "판매자 정보를 찾을 수 없습니다."),
  DUPLICATE_BUSINESS_NO(HttpStatus.CONFLICT, "이미 등록된 사업자 번호입니다."),
  DUPLICATE_BRAND_NAME(HttpStatus.CONFLICT, "이미 존재하는 브랜드 이름입니다."),
  /**
   * Product.
   */
  INVALID_PRICE(HttpStatus.BAD_REQUEST, "상품 가격이 0 이하입니다."),
  INVALID_DISCOUNT_RATE(HttpStatus.BAD_REQUEST, "할인율은 0~100 사이여야 합니다."),
  IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "이미지는 최소 1개 이상 등록해야 합니다."),
  IMAGE_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "이미지는 최대 5개까지 등록 가능합니다."),
  THUMBNAIL_REQUIRED(HttpStatus.BAD_REQUEST, "대표 이미지(isThumbnail=true)가 정확히 1개여야 합니다."),
  PRODUCT_HIDE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "판매 중이거나 품절 상태의 상품은 비공개로 변경할 수 없습니다."),
  SELLER_NOT_VERIFIED(HttpStatus.FORBIDDEN, "정산 계좌 인증이 완료되지 않았습니다."),
  SELLER_NOT_APPROVED(HttpStatus.FORBIDDEN, "판매자 승인 대기 상태입니다."),
  SELLER_ROLE_REQUIRED(HttpStatus.FORBIDDEN, "판매자 권한이 필요합니다."),
  PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
  PRODUCT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인 상품만 수정 또는 삭제할 수 있습니다."),
  PRODUCT_CORE_UPDATE_LOCKED(HttpStatus.BAD_REQUEST,
      "READY 또는 ON_SALE 상태에서는 상품명/가격/할인율을 수정할 수 없습니다."),
  INVALID_PRODUCT_STATUS_CHANGE(HttpStatus.BAD_REQUEST,
      "판매자 수동 상태 변경은 HIDDEN만 가능합니다."),
  PRODUCT_DELETE_NOT_ALLOWED(HttpStatus.BAD_REQUEST,
      "드랍 또는 주문 이력이 존재하는 상품은 삭제할 수 없습니다."),
  INVALID_IMAGE_FILE_TYPE(HttpStatus.BAD_REQUEST,
      "이미지 파일 타입은 jpeg, png, webp만 허용됩니다."),
  PRESIGNED_URL_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR,
      "Presigned URL 발급에 실패했습니다."),
  PRODUCT_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "상품 이미지를 찾을 수 없습니다."),
  THUMBNAIL_DELETE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "대표 이미지는 삭제할 수 없습니다."),
  IMAGE_MIN_REQUIRED(HttpStatus.BAD_REQUEST, "이미지는 최소 1개 이상 유지해야 합니다."),
  INVALID_STOCK(HttpStatus.BAD_REQUEST, "상품 재고는 0보다 커야 합니다."),
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청값 검증에 실패했습니다."),

  /**
   * Drop.
   */
  DROP_NOT_LIVE(HttpStatus.NOT_FOUND, "LIVE중인 드랍을 찾을 수 없습니다."),
  INVALID_DROP_DATE_RANGE(HttpStatus.BAD_REQUEST, "드랍 종료 시간은 시작 시간보다 뒤여야 합니다."),
  INVALID_DROP_TOTAL_STOCK(HttpStatus.BAD_REQUEST, "드랍 총 판매 수량은 0보다 커야 합니다."),
  INVALID_DROP_REMAIN_STOCK(HttpStatus.BAD_REQUEST, "잔여 수량은 0 이상이며 총 판매 수량 이하여야 합니다."),
  INVALID_DROP_PURCHASE_LIMIT(HttpStatus.BAD_REQUEST, "1인당 구매 제한 수량은 1 이상이어야 합니다."),
  DROP_TOTAL_STOCK_EXCEEDS_PRODUCT_STOCK(HttpStatus.BAD_REQUEST,
      "드랍 총 판매 수량은 상품 재고를 초과할 수 없습니다."),
  DROP_ACCESS_DENIED(HttpStatus.FORBIDDEN, "본인 상품의 드랍만 관리할 수 있습니다."),
  DROP_ALREADY_EXISTS(HttpStatus.BAD_REQUEST,
      "해당 상품에는 진행 중이거나 예정된 드랍이 이미 존재합니다."),
  DROP_ACTIVE_UPDATE_LOCKED(HttpStatus.BAD_REQUEST,
      "ACTIVE 상태에서는 드랍 시작 시간과 총 판매 수량을 수정할 수 없습니다."),
  DROP_UPDATE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "종료된 드랍은 수정할 수 없습니다."),
  DROP_ORDER_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "진행 중인 드랍에서만 주문할 수 있습니다."),
  DROP_PRODUCT_MISMATCH(HttpStatus.BAD_REQUEST, "요청 상품이 드랍 상품과 일치하지 않습니다."),
  DROP_DELETE_NOT_ALLOWED(HttpStatus.BAD_REQUEST,
      "주문 이력이 있거나 예정 상태가 아닌 드랍은 삭제할 수 없습니다."),
  DROP_STOP_NOT_ALLOWED(HttpStatus.BAD_REQUEST,
      "이미 종료된 드랍은 강제 종료할 수 없습니다."),
  INVALID_DROP_START_AT(HttpStatus.BAD_REQUEST, "INVALID_DROP_START_AT"),
  INVALID_DROP_ORDER_QUANTITY(HttpStatus.BAD_REQUEST, "주문 수량은 1 이상이어야 합니다."),

  /**
   * Order (세미 콜론 부분 변경 금지).
   */
  ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
  ORDER_CANCEL_FORBIDDEN(HttpStatus.FORBIDDEN, "본인 주문만 취소할 수 있습니다."),
  ORDER_DUPLICATE(HttpStatus.CONFLICT, "이미 존재하는 주문입니다."),
  ORDER_INVALID_STATUS(HttpStatus.BAD_REQUEST, "유효하지 않은 주문 상태 전이입니다."),
  ORDER_HOLD_EXPIRED(HttpStatus.BAD_REQUEST, "주문 홀드 시간이 만료되었습니다."),
  CONCURRENT_REQUEST_LOCKED(HttpStatus.CONFLICT, "동일 리소스를 처리 중입니다. 잠시 후 다시 시도해 주세요."),

  /**
   * Payment.
   */
  PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."),
  PAYMENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "해당 주문에 대한 결제가 이미 존재합니다."),
  PAYMENT_INVALID_STATUS(HttpStatus.BAD_REQUEST, "결제 상태가 올바르지 않습니다."),
  PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액이 주문 금액과 일치하지 않습니다."),
  PAYMENT_IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "이미 사용된 결제 요청 키입니다."),
  PAYMENT_TRANSACTION_ID_REQUIRED(HttpStatus.BAD_REQUEST, "거래 식별번호가 필요합니다."),
  PAYMENT_COMPLETE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "결제를 완료 처리할 수 없습니다."),
  PAYMENT_FAIL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "결제를 실패 처리할 수 없습니다."),
  PAYMENT_CONFIRM_FORBIDDEN(HttpStatus.BAD_REQUEST, "결제 확정을 진행할 수 없습니다."),
  PAYMENT_PORTONE_NOT_PAID(HttpStatus.BAD_REQUEST, "포트원 결제가 완료되지 않았습니다."),
  PAYMENT_PORTONE_MISMATCH(HttpStatus.BAD_REQUEST, "포트원 결제 정보가 내부 결제 정보와 일치하지 않습니다."),
  PAYMENT_PORTONE_API_ERROR(HttpStatus.BAD_GATEWAY, "포트원 결제 검증 중 오류가 발생했습니다."),
  PAYMENT_WEBHOOK_PAYMENT_ID_REQUIRED(HttpStatus.BAD_REQUEST, "웹훅 결제 식별자가 필요합니다."),
  PAYMENT_WEBHOOK_INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "웹훅 서명이 유효하지 않습니다."),
  PAYMENT_WEBHOOK_PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "웹훅 대상 결제를 찾을 수 없습니다."),

  /**
   * Refund.
   */
  REFUND_NOT_FOUND(HttpStatus.NOT_FOUND, "환불 요청을 찾을 수 없습니다."),
  REFUND_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 진행 중인 환불 요청이 있습니다."),
  REFUND_PAYMENT_NOT_COMPLETED(HttpStatus.BAD_REQUEST, "결제 완료된 건만 환불할 수 있습니다."),
  REFUND_ORDER_INVALID_STATUS(HttpStatus.BAD_REQUEST, "결제 완료된 주문만 환불할 수 있습니다."),
  REFUND_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "환불 금액이 결제 금액과 일치하지 않습니다."),
  REFUND_INVALID_STATUS(HttpStatus.BAD_REQUEST, "환불 상태가 올바르지 않습니다."),
  REFUND_PORTONE_API_ERROR(HttpStatus.BAD_GATEWAY, "포트원 환불 처리 중 오류가 발생했습니다."),

  // Wishlist
  EXISTS_BY_USER_AND_DROP(HttpStatus.CONFLICT, "이미 찜한 상품입니다."),
  DROP_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 드랍은 존재하지 않습니다.");


  private final HttpStatus status;
  private final String message;
}
