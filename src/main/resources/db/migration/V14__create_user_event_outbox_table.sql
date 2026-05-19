CREATE TABLE user_event_outbox
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic           VARCHAR(100)                        NOT NULL,
    message_key     VARCHAR(100)                        NOT NULL,
    payload         TEXT                                NOT NULL,
    status          VARCHAR(20)                         NOT NULL DEFAULT 'PENDING',
    attempts        INT                                 NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6),
    created_at      DATETIME(6)                         NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)                         NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_user_event_outbox_status_next_attempt (status, next_attempt_at)
);
