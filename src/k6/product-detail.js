import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '10s', target: 20 },
        { duration: '30s', target: 20 },
        { duration: '5s',  target: 0  },
    ],
    thresholds: {
        http_req_duration: ['p(95)<2000'],
        http_req_failed: ['rate<0.05'],
    },
};

const BASE = 'http://localhost:8080';

export default function () {
    const id = Math.floor(Math.random() * 50) + 1;
    check(http.get(`${BASE}/api/products/${id}`), { '2xx': r => r.status < 300 });
    sleep(0.1);
}
