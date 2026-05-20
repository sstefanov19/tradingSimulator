import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = 'http://localhost:8080';
const RUN_ID = uuidv4();

export const options = {
    stages: [
        { duration: '4s', target: 10 },   // ramp up to 10 users
        { duration: '12s', target: 30 },  // ramp up to 50 (beyond thread pool)
        { duration: '4s', target: 0 },    // ramp down
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],       // less than 1% errors
        http_req_duration: ['p(95)<2000'],    // 95% of requests under 2s
    },
};

// Runs once before the test — token shared across all virtual users
export function setup() {
    const res = http.post(`${BASE_URL}/api/v1/auth/login`, JSON.stringify({
        username: 'stefan',
        password: 'password',
    }), {
        headers: { 'Content-Type': 'application/json' },
    });

    check(res, { 'login succeeded': (r) => r.status === 200 });

    const { accessToken } = res.json();
    return { accessToken };
}

export default function (data) {
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${data.accessToken}`,
        'Idempotency-Key': `${RUN_ID}-${__VU}-${__ITER}`,
    };

    const payload = JSON.stringify({
        userId: 1,
        ticker: 'BTC',
        quantity: 0.1,
        orderType: 'BUY',
    });

    const res = http.post(`${BASE_URL}/api/v1/orders`, payload, { headers });

    console.log(`Status: ${res.status} | Body: ${res.body}`);

    check(res, {
        'status is 202': (r) => r.status === 202,
        'response time < 2s': (r) => r.timings.duration < 2000,
    });
}

// Idempotency race condition test — same key hammered by all VUs
export function idempotencyTest(data) {
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${data.accessToken}`,
        'Idempotency-Key': 'fixed-key-race-condition-test',
    };

    const payload = JSON.stringify({
        userId: 1,
        ticker: 'BTC',
        quantity: 1,
        orderType: 'BUY',
    });

    http.post(`${BASE_URL}/api/v1/orders`, payload, { headers });
}
