-- ============================================================
-- Exercise Analysis System — MySQL Schema
-- ============================================================

CREATE DATABASE IF NOT EXISTS exercise_db
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE exercise_db;

-- 운동 세션 마스터
CREATE TABLE exercise_sessions (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id       VARCHAR(50)  NOT NULL,
    exercise_type VARCHAR(20)  NOT NULL COMMENT 'SQUAT|PUSHUP|LUNGE|DEADLIFT',
    overall_score INT          NOT NULL DEFAULT 0,
    summary       TEXT,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user    (user_id),
    INDEX idx_type    (exercise_type),
    INDEX idx_created (created_at)
);

-- 부위별 피드백 (1세션 N피드백)
CREATE TABLE exercise_feedbacks (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT       NOT NULL,
    body_part       VARCHAR(40)  NOT NULL  COMMENT '예: 왼쪽 무릎',
    status          VARCHAR(10)  NOT NULL  COMMENT 'GOOD|WARNING|CAUTION',
    angle_measured  DECIMAL(6,2)           COMMENT '측정된 각도(도)',
    angle_ideal     VARCHAR(50)            COMMENT '이상 범위 설명',
    message         TEXT,
    FOREIGN KEY (session_id) REFERENCES exercise_sessions(id) ON DELETE CASCADE,
    INDEX idx_session (session_id),
    INDEX idx_status  (status)
);
