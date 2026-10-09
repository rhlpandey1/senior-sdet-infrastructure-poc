import http from 'k6/http';
import { check, sleep } from 'k6';
import encoding from 'k6/encoding';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const credentials = encoding.b64encode('sdet:sdet123');

export const options = {
  vus: 2,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const response = http.get(`${BASE_URL}/orders/ORD-1001`, {
    headers: {
      Authorization: `Basic ${credentials}`,
      Accept: 'application/json',
    },
    tags: { endpoint: 'get-order' },
  });

  check(response, {
    'status is 200': (r) => r.status === 200,
    'response contains order ID': (r) =>
      r.json('id') === 'ORD-1001',
  });

  sleep(1);
}
