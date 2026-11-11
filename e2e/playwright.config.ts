import path from 'node:path';
import { defineConfig } from '@playwright/test';

/**
 * The stack under test: the backend in its "memory" profile (no database, sample data) and the admin web.
 * By default both are started here on their own ports (so they never clash with the docker compose stack);
 * set E2E_BASE_URL / E2E_API_URL to run against a stack that is already up (the compose "e2e" profile).
 *
 * Build them first:  pnpm --filter @itera/e2e prepare:stack
 */
const apiUrl = process.env.E2E_API_URL ?? 'http://localhost:18080';
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:13000';
const external = Boolean(process.env.E2E_BASE_URL);

const root = path.resolve(import.meta.dirname, '..');

export default defineConfig({
  testDir: './tests',
  // One shared in-memory backend: tests reset it, so they run one after another.
  workers: 1,
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 30_000,
  expect: { timeout: 7_000 },
  use: {
    baseURL,
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
  webServer: external
    ? undefined
    : [
        {
          name: 'backend (memory profile)',
          command: 'java -Dspring.profiles.active=memory -jar services/backend-api/build/libs/backend-api-1.0.0.jar',
          cwd: root,
          // the unauthenticated reset endpoint is off by default; the tests need it
          env: { PORT: '18080', MEMORY_RESET_ENDPOINT: 'true' },
          url: `${apiUrl}/api/v1/health`,
          reuseExistingServer: !process.env.CI,
          timeout: 60_000,
        },
        {
          name: 'admin web',
          command: 'pnpm exec next start -p 13000',
          cwd: path.join(root, 'apps/admin'),
          env: { API_BASE_URL: apiUrl },
          url: `${baseURL}/admin/login`,
          reuseExistingServer: !process.env.CI,
          timeout: 60_000,
        },
      ],
});
