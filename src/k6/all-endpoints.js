import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '10s', target: 30 },
        { duration: '60s', target: 30 },
        { duration: '5s',  target: 0  },
    ],
    thresholds: {
        http_req_duration: ['p(95)<2000'],
        // 인증 불필요 엔드포인트만 실패율 측정 (401은 의도적 요청이므로 제외)
        'http_req_failed{expected_response:true}': ['rate<0.01'],
    },
};

const BASE = 'http://localhost:8080';

export default function () {
    const r = Math.random();

    if (r < 0.35) {
        // 상품 목록 — 가장 빈번
        check(http.get(`${BASE}/api/products?size=20`),          { '200': r => r.status === 200 });
    } else if (r < 0.55) {
        // 상품 상세 (seed 데이터 ID 1~50 랜덤)
        const id = Math.floor(Math.random() * 50) + 1;
        check(http.get(`${BASE}/api/products/${id}`),            { '2xx': r => r.status < 300 });
    } else if (r < 0.65) {
        // 카테고리
        check(http.get(`${BASE}/api/products/categories`),       { '2xx': r => r.status < 300 });
    } else if (r < 0.72) {
        // 카테고리 메인
        check(http.get(`${BASE}/api/products/categories/main`),  { '2xx': r => r.status < 300 });
    } else if (r < 0.79) {
        // 알림 (인증 없음 → 401 의도적)
        check(http.get(`${BASE}/api/notifications`),             { 'got response': r => r.status > 0 });
    } else if (r < 0.85) {
        // 내 프로필 (인증 없음 → 401 의도적)
        check(http.get(`${BASE}/api/member/me`),                 { 'got response': r => r.status > 0 });
    } else if (r < 0.90) {
        // 장바구니 (인증 없음 → 401 의도적)
        check(http.get(`${BASE}/cart`),                          { 'got response': r => r.status > 0 });
    } else if (r < 0.95) {
        // 헬스체크
        check(http.get(`${BASE}/healthz`),                       { '200': r => r.status === 200 });
    } else {
        // 존재하지 않는 경로 → 인증 없을 시 401, 인증 있을 시 404
        check(http.get(`${BASE}/api/not-found-test`),            { 'got response': r => r.status > 0 });
    }

    sleep(0.1);
}
