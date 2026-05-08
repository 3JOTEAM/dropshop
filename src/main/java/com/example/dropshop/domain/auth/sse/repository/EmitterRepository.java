package com.example.dropshop.domain.auth.sse.repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Emitter 리포지토리 인터페이스.
 */
public interface EmitterRepository {

  Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

  SseEmitter save(String emitterId, SseEmitter emitter);

  void saveEventCache(String emitterId, Object event);

  Map<String, SseEmitter> findAllEmitterStartWithByUserId(String userId);

  Map<String, Object> findAllEventCacheStartWithByUserId(String userId);

  SseEmitter findEmitterByEmail(String email);

  void deleteByEmail(String email);

  void deleteById(String emitterId);

  void deleteAllEmitterStartWithId(String userId);

  void deleteAllEventCacheStartWithId(String userId);
}
