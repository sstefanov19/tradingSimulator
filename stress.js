import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = 'http://localhost:8080';
const RUN_ID = uuidv4();
const USER_COUNT = 100;
const JSON_HEADERS = { 'Content-Type': 'application/json' };

export const options = {
    stages: [
        { duration: '10s', target: 30 },  // ramp up to 30
        { duration: '10s', target: 100 }, // ramp up to 100
        { duration: '30s', target: 100 }, // hold at 100 — steady state
        { duration: '10s', target: 0 },   // ramp down
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],       // less than 1% errors
        http_req_duration: ['p(95)<2000'],    // 95% of requests under 2s
    },
};

// Runs once — registers USER_COUNT users, gives each a balance, returns their credentials.
export function setup() {
    const users = [];

    for (let i = 1; i <= USER_COUNT; i++) {
        const username = `ltuser${i}`;
        const password = 'password123';
        const email = `ltuser${i}@test.com`;

        // Register — ignore 4xx if user already exists from a previous run.
        http.post(`${BASE_URL}/api/v1/auth/register`,
            JSON.stringify({ username, email, password }),
            { headers: JSON_HEADERS }
        );

        // Login — always succeeds whether this was a fresh register or an existing user.
        const loginRes = http.post(`${BASE_URL}/api/v1/auth/login`,
            JSON.stringify({ username, password }),
            { headers: JSON_HEADERS }
        );
        check(loginRes, { 'setup login succeeded': (r) => r.status === 200 });

        const { accessToken, userId } = loginRes.json();

        // Deposit enough balance to cover the entire test run.
        http.post(`${BASE_URL}/api/v1/balance/deposit`,
            JSON.stringify({ userId, amount: 1000000 }),
            { headers: { ...JSON_HEADERS, 'Authorization': `Bearer ${accessToken}` } }
        );

        users.push({ userId, accessToken });
    }

    return { users };
}

export default function (data) {
    const user = data.users[(__VU - 1) % data.users.length];

    const headers = {
        ...JSON_HEADERS,
        'Authorization': `Bearer ${user.accessToken}`,
        'Idempotency-Key': `${RUN_ID}-${__VU}-${__ITER}`,
    };

    const payload = JSON.stringify({
        userId: user.userId,
        ticker: 'BTC',
        quantity: 0.1,
        orderType: 'BUY',
    });

    const res = http.post(`${BASE_URL}/api/v1/orders`, payload, { headers });

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
