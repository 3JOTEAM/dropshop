/**
 * 시나리오 1: 로그인 부하 테스트
 *
 * 목적: 로그인 API (POST /api/auth/login) 처리량 및 응답시간 측정
 *       - BCrypt 비밀번호 검증 + JWT 발급 + Redis 토큰 저장 성능 확인
 *
 * 실행:
 *   k6 run k6/01_login.js
 *   k6 run -e BASE_URL=http://localhost:8080 k6/01_login.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';
import { BASE_URL, THRESHOLDS, JSON_HEADERS } from './config.js';

const loginDuration = new Trend('login_duration');
const loginFailRate  = new Rate('login_fail_rate');

export const options = {
  thresholds: {
    ...THRESHOLDS,
    login_duration: ['p(95)<800'],  // 로그인은 BCrypt 연산으로 여유 있게 설정
    login_fail_rate: ['rate<0.01'],
  },
  scenarios: {
    // 점진적 부하 증가 (Ramp-up)
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },  // 0 → 20 VU
        { duration: '1m',  target: 50 },  // 20 → 50 VU 유지
        { duration: '30s', target: 0  },  // 감소
      ],
    },
  },
};

// 테스트 유저 목록 (DB에 미리 생성 필요)
const TEST_USERS = [
  { email: 'user1@test.com', password: 'Password1!' },
  { email: 'user2@test.com', password: 'Password1!' },
  { email: 'user3@test.com', password: 'Password1!' },
  { email: 'user4@test.com', password: 'Password1!' },
  { email: 'user5@test.com', password: 'Password1!' },
];

export default function () {
  const user = TEST_USERS[Math.floor(Math.random() * TEST_USERS.length)];

  const res = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: user.email, password: user.password }),
    { headers: JSON_HEADERS }
  );

  loginDuration.add(res.timings.duration);

  const ok = check(res, {
    '로그인 성공 (200)':         (r) => r.status === 200,
    'accessToken 존재':          (r) => {
      try { return !!JSON.parse(r.body).data; } catch { return false; }
    },
  });

  loginFailRate.add(!ok);

  sleep(1);
}
