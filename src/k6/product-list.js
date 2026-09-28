import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const p95 = new Trend('p95_response_time', true);
const errorRate = new Rate('error_rate');

export const options = {
    stages: [
        { duration: '10s', target: 20 },  // ramp-up
        { duration: '30s', target: 20 },  // steady
        { duration: '5s',  target: 0  },  // ramp-down
    ],
    thresholds: {
        http_req_duration: ['p(95)<5000'],
        error_rate: ['rate<0.01'],
    },
};

export default function () {
    const res = http.get('http://localhost:8080/api/products?size=20');
    const ok = check(res, { 'status 200': (r) => r.status === 200 });
    p95.add(res.timings.duration);
    errorRate.add(!ok);
}
