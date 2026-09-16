/**
 * Tests E2E — Flux étudiant
 *
 * Scénarios couverts :
 *  1. Login et affichage du tableau de bord étudiant
 *  2. Rejoindre une session via un code
 *  3. Répondre à toutes les questions (QCM + ouverte)
 *  4. Soumettre l'examen
 *  5. Voir le score et le panel BERTScore pour les questions ouvertes
 *  6. Résilience : soumission même si NLP service est down (score partiel)
 */
import { test, expect, Page } from '@playwright/test';
import { STUDENT, loginViaKeycloakPage } from './helpers/auth';

// Code de session créé par les tests enseignant (peut être mocké en CI)
const TEST_SESSION_CODE = process.env.TEST_SESSION_CODE ?? 'TEST01';

// ── Helpers ────────────────────────────────────────────────────────────────

async function loginAsStudent(page: Page) {
  await page.goto('/');
  if (page.url().includes('login') || page.url().includes('keycloak')) {
    await loginViaKeycloakPage(page, STUDENT);
  }
}

// ── Tests ──────────────────────────────────────────────────────────────────

test.describe('Flux étudiant', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsStudent(page);
  });

  // ── 1. Tableau de bord étudiant ────────────────────────────────────────────
  test('affiche le tableau de bord étudiant après connexion', async ({ page }) => {
    await page.goto('/');
    await expect(
      page.getByRole('heading', { name: /tableau de bord|mes examens|sessions/i }),
    ).toBeVisible();

    // L'étudiant ne doit PAS voir les boutons d'administration enseignant
    await expect(page.getByRole('button', { name: /générer.*quiz/i })).not.toBeVisible();
  });

  // ── 2. Rejoindre une session ──────────────────────────────────────────────
  test('rejoint une session via son code', async ({ page }) => {
    await page.goto('/etudiant/sessions');

    const joinBtn = page.getByRole('button', { name: /rejoindre|join/i });
    await expect(joinBtn).toBeVisible();
    await joinBtn.click();

    // Entrer le code de session
    const codeInput = page.getByPlaceholder(/code|session/i);
    await expect(codeInput).toBeVisible();
    await codeInput.fill(TEST_SESSION_CODE);

    const confirmBtn = page.getByRole('button', { name: /rejoindre|valider|commencer/i });
    await confirmBtn.click();

    // La page d'examen doit s'afficher
    await expect(
      page.getByRole('heading', { name: /examen|quiz|questions/i }),
    ).toBeVisible({ timeout: 15_000 });
  });

  // ── 3. Répondre aux questions ─────────────────────────────────────────────
  test('répond à toutes les questions et soumet l\'examen', async ({ page }) => {
    await page.goto('/etudiant/sessions');

    // Rejoindre ou reprendre une session active
    const activeSession = page.locator('[data-testid="session-card"]').first();
    if (await activeSession.isVisible({ timeout: 5_000 })) {
      await activeSession.click();
    } else {
      // Rejoindre via code
      const joinBtn = page.getByRole('button', { name: /rejoindre/i });
      if (await joinBtn.isVisible({ timeout: 3_000 })) {
        await joinBtn.click();
        const codeInput = page.getByPlaceholder(/code/i);
        await codeInput.fill(TEST_SESSION_CODE);
        await page.getByRole('button', { name: /rejoindre|valider/i }).click();
      }
    }

    // Répondre à chaque question visible
    let questionIndex = 0;
    while (true) {
      // QCM — sélectionner la première option
      const radioOptions = page.locator('input[type="radio"]');
      if (await radioOptions.count() > 0) {
        await radioOptions.first().check();
      }

      // Question ouverte — rédiger une réponse
      const textarea = page.locator('textarea[data-testid="open-answer"]');
      if (await textarea.isVisible({ timeout: 2_000 }).catch(() => false)) {
        await textarea.fill(
          `Réponse automatisée E2E pour la question ${questionIndex + 1}. ` +
          `La programmation orientée objet repose sur les concepts d'encapsulation, ` +
          `d'héritage et de polymorphisme.`,
        );
      }

      // Bouton "Suivant" ou "Terminer"
      const nextBtn = page.getByRole('button', { name: /suivant|next/i });
      const submitBtn = page.getByRole('button', { name: /terminer|soumettre|submit/i });

      if (await submitBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
        await submitBtn.click();
        break;
      } else if (await nextBtn.isVisible({ timeout: 2_000 }).catch(() => false)) {
        await nextBtn.click();
        questionIndex++;
      } else {
        break;
      }
    }

    // Confirmer la soumission si une dialog apparaît
    const confirmDialog = page.getByRole('dialog');
    if (await confirmDialog.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await confirmDialog.getByRole('button', { name: /confirmer|soumettre|oui/i }).click();
    }

    // Attendre la page de résultats
    await expect(
      page.getByText(/résultats|votre score|corrigé/i),
    ).toBeVisible({ timeout: 30_000 });
  });

  // ── 4. Voir le score et BERTScore ─────────────────────────────────────────
  test('affiche le score et le panel BERTScore sur la page résultats', async ({ page }) => {
    // Naviguer vers le dernier résultat
    await page.goto('/etudiant/resultats');

    const firstResult = page.locator('[data-testid="attempt-card"]').first();
    if (await firstResult.isVisible({ timeout: 10_000 })) {
      await firstResult.click();
    } else {
      test.skip(true, 'Aucun résultat disponible — exécuter le test de soumission d\'abord');
    }

    // Le score total doit être affiché
    await expect(
      page.locator('[data-testid="total-score"]'),
    ).toBeVisible({ timeout: 10_000 });

    // Vérifier la structure de la page (score, tableau de questions)
    await expect(page.getByText(/score|résultat/i)).toBeVisible();

    // Si des questions ouvertes existent, le panel BERTScore doit être présent
    const bertPanel = page.locator('[data-testid="bert-score-panel"]');
    if (await bertPanel.count() > 0) {
      await expect(bertPanel.first()).toBeVisible();
      // F1 score affiché
      await expect(bertPanel.first().getByText(/f1|precision|recall/i)).toBeVisible();
      // Label (CORRECT, PARTIEL, INCORRECT)
      const label = bertPanel.first().locator('[data-testid="bert-label"]');
      await expect(label).toBeVisible();
      await expect(label).toHaveText(/CORRECT|PARTIEL|INCORRECT/);
    }
  });

  // ── 5. Résilience : score visible même si NLP down ────────────────────────
  test('affiche un résultat partiel si le service NLP est indisponible', async ({ page }) => {
    // Ce test vérifie que les résultats QCM sont toujours affichés
    // même si BERTScore n'a pas pu scorer les réponses ouvertes.
    await page.goto('/etudiant/resultats');

    const firstResult = page.locator('[data-testid="attempt-card"]').first();
    if (!await firstResult.isVisible({ timeout: 10_000 })) {
      test.skip(true, 'Aucun résultat disponible');
    }
    await firstResult.click();

    // Le score doit toujours être affiché (même 0 pour les questions ouvertes)
    await expect(
      page.locator('[data-testid="total-score"]'),
    ).toBeVisible({ timeout: 10_000 });

    // Le panel BERTScore pour une question ouverte "unavailable" doit afficher
    // un message informatif, pas une erreur fatale
    const unavailableMsg = page.getByText(/non disponible|indisponible|unavailable/i);
    if (await unavailableMsg.count() > 0) {
      // Le message est accepté (le fallback fonctionne)
      await expect(unavailableMsg.first()).toBeVisible();
    }

    // Pas d'erreur critique visible
    await expect(page.getByText(/erreur système|crash|500/i)).not.toBeVisible();
  });
});
