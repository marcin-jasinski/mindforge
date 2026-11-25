import { defineConfig, devices } from '@playwright/test';

// Playwright runs this file in Node; declared here so the suite needs no @types/node dependency.
declare const process: { env: Record<string, string | undefined> };

// Browser E2E against the compose stack (compose.yml + compose.e2e.yml); Maven never runs it (D07, D08).
export default defineConfig({
  testDir: './specs',
  outputDir: 'test-results',
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:8080',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
