import { defineConfig, devices } from '@playwright/test';

/**
 * Configuration Playwright pour QuizGen E2E.
 *
 * Variables d'environnement requises (CI ou .env.test) :
 *   BASE_URL          URL du frontend (défaut : http://localhost:80/frontend)
 *   KC_URL            URL Keycloak    (défaut : http://localhost:8180)
 *   KC_REALM          Realm Keycloak  (défaut : quizgen)
 *   TEST_TEACHER_USER Login enseignant de test
 *   TEST_TEACHER_PASS Mot de passe enseignant
 *   TEST_STUDENT_USER Login étudiant de test
 *   TEST_STUDENT_PASS Mot de passe étudiant
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,   // Les tests partagent un état Keycloak
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['junit', { outputFile: 'results/junit.xml' }],
  ],
  use: {
    baseURL:         process.env.BASE_URL ?? 'http://localhost:80/frontend',
    trace:           'on-first-retry',
    screenshot:      'only-on-failure',
    video:           'retain-on-failure',
    actionTimeout:   15_000,
    navigationTimeout: 30_000,
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  // Timeout global par test
  timeout: 120_000,
  expect: {
    timeout: 10_000,
  },
});
