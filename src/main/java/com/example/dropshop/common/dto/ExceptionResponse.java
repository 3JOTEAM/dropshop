package com.example.dropshop.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import lombok.Setter;

@Getter
@Builder
public class ExceptionResponse {

  private final int errorCode;
  private final String message;
  private final String path;
  private final LocalDateTime time;

  public static ExceptionResponse from(int errorCode, String message, String path) {
    return ExceptionResponse.builder()
        .errorCode(errorCode)
        .message(message)
        .path(path)
        .time(LocalDateTime.now())
        .build();
  }
}