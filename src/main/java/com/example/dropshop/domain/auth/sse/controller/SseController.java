package com.example.dropshop.domain.auth.sse.controller;

import com.example.dropshop.domain.auth.sse.service.SseEmitterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 구독 엔드포인트.
 *
 * <p>클라이언트는 로그인 직후 GET /api/sse/subscribe 를 호출해
 * 서버로부터 실시간 이벤트(force-logout 등)를 수신할 수 있다.
 *
 * <p>프론트엔드 사용 예시:
 * <pre>
 *   const es = new EventSource('/api/sse/subscribe');
 *   es.addEventListener('force-logout', () => {
 *     alert('다른 기기에서 로그인되었습니다.');
 *     // 로컬 토큰 삭제 후 로그인 페이지 이동
 *   });
 * </pre>
 */
@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
@Tag(name = "SSE", description = "실시간 서버 전송 이벤트 API")
@SecurityRequirement(name = "bearerAuth")
public class SseController {

  private final SseEmitterService sseEmitterService;

  /**
   * SSE 구독. Spring Security가 설정한 인증 정보에서 이메일을 추출한다.
   *
   * @param userEmail   유저 이메일.
   * @param lastEventId 마지막 이벤트 아이디.
   * @return SseEmitter (text/event-stream)
   */
  @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @Operation(summary = "SSE 연결", description = "로그인한 사용자의 실시간 이벤트 스트림 연결을 생성합니다.")
  public ResponseEntity<SseEmitter> subscribe(
      @AuthenticationPrincipal String userEmail,
      @RequestHeader(value = "Last-Event-ID", required = false, defaultValue = "") String lastEventId) {

    return ResponseEntity.ok(sseEmitterService.subscribe(userEmail, lastEventId));
  }
}
