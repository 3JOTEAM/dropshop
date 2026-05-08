package com.example.dropshop.common.constant.kafka;

/** 매직 넘버들. */
public class MagicNumbers {
  public static final int FIVE = 5;
  public static final long FIVE_MINUTES = 60 * FIVE;
  public static final Long THRESHOLD = 10L;
  public static final Long PROCESS_TIME = 10L;
  public static final int BATCH_SIZE = 100;
  public static final int CACHE_LIMIT = 1024 * 1024; // 1MB
  public static final Long SSE_TIMEOUT_MS = 30 * 60 * 1000L;
}
