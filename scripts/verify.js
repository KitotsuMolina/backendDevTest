import http from 'k6/http';
import { check, sleep } from 'k6';

// Complement to the supplied test: the original makes requests but has no assertions.
export const options = {
  scenarios: {
    normal: { executor: 'constant-vus', vus: 200, duration: '10s', exec: 'normal', gracefulStop: '0s' },
    notFound: { executor: 'constant-vus', vus: 200, duration: '10s', exec: 'notFound', startTime: '10s', gracefulStop: '0s' },
    error: { executor: 'constant-vus', vus: 200, duration: '10s', exec: 'error', startTime: '20s', gracefulStop: '0s' },
    slow: { executor: 'constant-vus', vus: 200, duration: '10s', exec: 'slow', startTime: '30s', gracefulStop: '10s' },
    verySlow: { executor: 'constant-vus', vus: 200, duration: '10s', exec: 'verySlow', startTime: '50s', gracefulStop: '30s' },
  },
  thresholds: {
    checks: ['rate==1'],
    'http_req_duration{scenario:normal}': ['p(95)<2000'],
    'http_req_duration{scenario:notFound}': ['p(95)<2000'],
    'http_req_duration{scenario:error}': ['p(95)<2000'],
    'http_req_duration{scenario:slow}': ['p(95)<7500'],
    'http_req_duration{scenario:verySlow}': ['p(95)<7500'],
  },
};
const host = 'http://host.docker.internal:5000';
function verify(id, expected) {
  const response = http.get(`${host}/product/${id}/similar`);
  check(response, {
    'status 200': r => r.status === 200,
    'ordered expected products': r => {
      try { return JSON.stringify(r.json().map(p => p.id)) === JSON.stringify(expected); }
      catch (_) { return false; }
    },
  });
  sleep(0.5);
}
export function normal() { verify('1', ['2', '3', '4']); }
export function notFound() { verify('4', ['1', '2']); }
export function error() { verify('5', ['1', '2']); }
export function slow() { verify('2', ['3', '100', '1000']); }
export function verySlow() { verify('3', ['100', '1000']); }
