/**
 * Helpers d'authentification Keycloak pour les tests E2E.
 * Utilise le Direct Access Grant (Resource Owner Password Credentials)
 * disponible en environnement de test.
 */
import { Page, BrowserContext, expect } from '@playwright/test';

const KC_URL   = process.env.KC_URL   ?? 'http://localhost:8180';
const KC_REALM = process.env.KC_REALM ?? 'quizgen';

export interface TestUser {
  username: string;
  password: string;
}

export const TEACHER: TestUser = {
  username: process.env.TEST_TEACHER_USER ?? 'prof.test@quizgen.ma',
  password: process.env.TEST_TEACHER_PASS ?? 'Test1234!',
};

export const STUDENT: TestUser = {
  username: process.env.TEST_STUDENT_USER ?? 'etudiant.test@quizgen.ma',
  password: process.env.TEST_STUDENT_PASS ?? 'Test1234!',
};

/**
 * Obtient un token d'accès Keycloak via le Direct Access Grant.
 * Utilisé pour pré-authentifier le contexte navigateur (session storage).
 */
export async function getKeycloakToken(user: TestUser): Promise<string> {
  const params = new URLSearchParams({
    grant_type: 'password',
    client_id:  'quizgen-frontend',
    username:   user.username,
    password:   user.password,
  });

  const res = await fetch(
    `${KC_URL}/realms/${KC_REALM}/protocol/openid-connect/token`,
    {
      method:  'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body:    params.toString(),
    },
  );

  if (!res.ok) {
    const text = await res.text();
    throw new Error(
      `Keycloak token request failed (${res.status}): ${text}\n` +
      `Vérifier que TEST_TEACHER_USER/TEST_STUDENT_USER sont configurés.`,
    );
  }

  const data = await res.json();
  return data.access_token as string;
}

/**
 * Navigue vers la page de login Keycloak et remplit les identifiants.
 * Attendu uniquement quand le Direct Access Grant n'est pas disponible.
 */
export async function loginViaKeycloakPage(
  page: Page,
  user: TestUser,
): Promise<void> {
  // Cliquer sur le bouton "Se connecter" dans l'app
  await page.getByRole('button', { name: /se connecter|connexion|login/i }).click();

  // La page est redirigée vers Keycloak
  await page.waitForURL(/realms\/quizgen\/protocol\/openid-connect\/auth/);

  await page.getByLabel(/username|nom d'utilisateur|identifiant/i).fill(user.username);
  await page.getByLabel(/password|mot de passe/i).fill(user.password);
  await page.getByRole('button', { name: /sign in|connexion|se connecter/i }).click();

  // Attendre le retour sur l'app
  await page.waitForURL(/\/frontend/);
  await expect(page).not.toHaveURL(/\/login/);
}

/**
 * Injecte le token dans le localStorage pour simuler une session authentifiée.
 * Plus rapide que le login UI — à utiliser quand l'app lit le token depuis localStorage.
 */
export async function injectToken(
  context: BrowserContext,
  token: string,
): Promise<void> {
  await context.addInitScript((t) => {
    localStorage.setItem('quizgen_access_token', t);
  }, token);
}
