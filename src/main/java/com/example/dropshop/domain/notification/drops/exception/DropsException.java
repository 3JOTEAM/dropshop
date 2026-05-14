package com.example.dropshop.domain.notification.drops.exception;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.exception.ServiceException;
import lombok.Getter;

/** 드랍 도메인 전용 예외. */
@Getter
public class DropsException extends ServiceException {

  private final ErrorCode errorCode;

  /**
   * 에러 코드를 기반으로 드랍 예외를 생성한다.
   *
   * @param errorCode 공통 에러 코드
   */
  public DropsException(ErrorCode errorCode) {
    super(errorCode);
    this.errorCode = errorCode;
  }

  /**
   * 에러 코드와 사용자 메시지로 드랍 예외를 생성한다.
   *
   * @param errorCode 공통 에러 코드
   * @param message 상세 메시지
   */
  public DropsException(ErrorCode errorCode, String message) {
    super(errorCode, message);
    this.errorCode = errorCode;
  }
}
