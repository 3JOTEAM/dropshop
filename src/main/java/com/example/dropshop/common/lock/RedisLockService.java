package com.example.dropshop.common.lock;

import com.example.dropshop.common.exception.ErrorCode;
import com.example.dropshop.common.exception.ServiceException;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis 기반 분산 락 서비스.
 *
 * <p>락 키별로 단일 작업만 실행되도록 보장하며, 락 획득 실패 시 일정 시간 동안 재시도한다.
 * 락 해제는 저장된 토큰을 검증한 뒤 수행해, 만료된 락이 다른 요청에 의해 다시 획득된 경우에도
 * 이전 요청이 잘못된 락을 제거하지 않도록 보호한다.
 */
@Service
public class RedisLockService {

  private static final Duration DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration DEFAULT_LEASE_TIMEOUT = Duration.ofSeconds(30);
  private static final long RETRY_DELAY_MILLIS = 50L;

  /** 락 소유자 토큰이 일치할 때만 키를 삭제하는 Lua 스크립트. */
  private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

  static {
    UNLOCK_SCRIPT = new DefaultRedisScript<>();
    UNLOCK_SCRIPT.setScriptText(
        "if redis.call('get', KEYS[1]) == ARGV[1] then\n"
            + "  return redis.call('del', KEYS[1])\n"
            + "end\n"
            + "return 0"
    );
    UNLOCK_SCRIPT.setResultType(Long.class);
  }

  private final StringRedisTemplate stringRedisTemplate;

  /**
   * RedisTemplate을 주입받아 락 서비스를 생성한다.
   *
   * @param stringRedisTemplate Redis 문자열 템플릿
   */
  public RedisLockService(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  /**
   * 지정한 키에 대한 락을 획득한 뒤 콜백을 실행하고 결과를 반환한다.
   *
   * <p>기본 대기 시간과 임대 시간을 사용하며, 제한 시간 내에 락을 얻지 못하면
   * {@link ServiceException}을 던진다.
   *
   * @param key 락 키
   * @param callback 락 획득 후 실행할 작업
   * @param <T> 반환 타입
   * @return 콜백 실행 결과
   */
  public <T> T executeWithLock(String key, LockCallback<T> callback) {
    String token = UUID.randomUUID().toString();
    long deadline = System.nanoTime() + DEFAULT_WAIT_TIMEOUT.toNanos();

    while (true) {
      if (acquire(key, token, DEFAULT_LEASE_TIMEOUT)) {
        break;
      }
      if (System.nanoTime() >= deadline) {
        throw new ServiceException(ErrorCode.CONCURRENT_REQUEST_LOCKED);
      }
      sleepRetryDelay();
    }

    try {
      return callback.doInLock();
    } finally {
      release(key, token);
    }
  }

  /**
   * 지정한 키에 대한 락을 한 번만 시도해 획득한 경우에만 작업을 실행한다.
   *
   * @param key 락 키
   * @param callback 락 획득 시 실행할 작업
   * @return 락 획득 및 작업 실행 성공 여부
   */
  public boolean tryExecuteWithLock(String key, LockRunnable callback) {
    return tryExecuteWithLock(key, DEFAULT_LEASE_TIMEOUT, callback);
  }

  /**
   * 지정한 임대 시간으로 락 획득을 한 번만 시도한 뒤, 성공한 경우에만 작업을 실행한다.
   *
   * @param key 락 키
   * @param leaseTimeout 락 임대 시간
   * @param callback 락 획득 시 실행할 작업
   * @return 락 획득 및 작업 실행 성공 여부
   */
  public boolean tryExecuteWithLock(String key, Duration leaseTimeout, LockRunnable callback) {
    String token = UUID.randomUUID().toString();
    if (acquire(key, token, leaseTimeout)) {
      try {
        callback.doInLock();
        return true;
      } finally {
        release(key, token);
      }
    }
    return false;
  }

  private boolean acquire(String key, String token, Duration leaseTimeout) {
    Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(key, token, leaseTimeout);
    return Boolean.TRUE.equals(locked);
  }

  /**
   * 현재 저장된 토큰이 요청 토큰과 같을 때만 락을 해제한다.
   *
   * <p>토큰 비교와 삭제를 Redis 안에서 원자적으로 수행해, 만료 후 다른 요청이 다시 잡은
   * 락을 이전 요청이 지우지 않도록 보호한다.
   */
  private void release(String key, String token) {
    stringRedisTemplate.execute(
        UNLOCK_SCRIPT,
        Collections.singletonList(key),
        token
    );
  }

  private void sleepRetryDelay() {
    try {
      Thread.sleep(RETRY_DELAY_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ServiceException(ErrorCode.CONCURRENT_REQUEST_LOCKED);
    }
  }

  /** 락을 획득한 구간에서 값을 반환하는 작업 계약. */
  @FunctionalInterface
  public interface LockCallback<T> {

    /**
     * 락을 획득한 구간에서 실행할 값을 반환하는 작업.
     *
     * @return 작업 결과
     */
    T doInLock();
  }

  /** 락을 획득한 구간에서 실행할 반환값 없는 작업 계약. */
  @FunctionalInterface
  public interface LockRunnable {

    /**
     * 락을 획득한 구간에서 실행할 반환값 없는 작업.
     */
    void doInLock();
  }
}
