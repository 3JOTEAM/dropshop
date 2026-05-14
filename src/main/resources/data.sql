-- =====================================================
-- 로컬 테스트용 초기 데이터
-- 서버 시작 시 자동 실행 (ddl-auto: create-drop 환경)
-- =====================================================

-- Admin 계정 자동 생성
-- 이메일: admin@test.com / 비밀번호: Test1234!
INSERT IGNORE INTO users (email, password, nickname, status, role, created_at, modified_at)
VALUES (
  'admin@test.com',
  '$2a$10$UVg.BRQCweVzdNxImL9wpeMsNKm8Yi6j00XflpRJsVKqIK4Ycf5h6',
  '관리자',
  'ACTIVE',
  'ADMIN',
  NOW(),
  NOW()
);
