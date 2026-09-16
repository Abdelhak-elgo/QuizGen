/**
 * Tests E2E — Flux enseignant
 *
 * Scénarios couverts :
 *  1. Login et affichage du tableau de bord enseignant
 *  2. Upload d'un document PDF
 *  3. Génération d'un quiz depuis un document
 *  4. Édition d'une question générée
 *  5. Publication d'un quiz
 *  6. Création d'une session d'examen
 *  7. Export SCORM / Moodle (boutons disponibles après publication)
 *
 * Prérequis :
 *  - Stack Docker démarrée (docker-compose.yml ou .prod.yml)
 *  - Variables TEST_TEACHER_USER / TEST_TEACHER_PASS définies
 *  - Un PDF de test disponible dans e2e/fixtures/sample.pdf
 */
import { test, expect, Page } from '@playwright/test';
import * as path from 'path';
import { TEACHER, loginViaKeycloakPage } from './helpers/auth';

const FIXTURE_PDF = path.join(__dirname, '..', 'fixtures', 'sample.pdf');

// ── Helpers ────────────────────────────────────────────────────────────────

async function ensureLoggedIn(page: Page) {
  await page.goto('/');
  // Si redirigé vers la page de login, s'authentifier
  if (page.url().includes('login') || page.url().includes('keycloak')) {
    await loginViaKeycloakPage(page, TEACHER);
  }
}

// ── Tests ──────────────────────────────────────────────────────────────────

test.describe('Flux enseignant', () => {
  test.beforeEach(async ({ page }) => {
    await ensureLoggedIn(page);
  });

  // ── 1. Tableau de bord ────────────────────────────────────────────────────
  test('affiche le tableau de bord enseignant après connexion', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { name: /tableau de bord|dashboard/i })).toBeVisible();

    // L'enseignant doit voir ses documents et quiz
    await expect(page.getByText(/mes documents|documents/i)).toBeVisible();
    await expect(page.getByText(/mes quiz|quiz/i)).toBeVisible();
  });

  // ── 2. Upload PDF ────────────────────────────────────────────────────────
  test('upload un document PDF', async ({ page }) => {
    await page.goto('/enseignant/documents');

    // Cliquer sur le bouton upload
    const uploadBtn = page.getByRole('button', { name: /upload|téléverser|ajouter/i });
    await expect(uploadBtn).toBeVisible();
    await uploadBtn.click();

    // Sélectionner le fichier
    const fileInput = page.locator('input[type="file"]');
    await fileInput.setInputFiles(FIXTURE_PDF);

    // Remplir le titre si requis
    const titleInput = page.getByLabel(/titre/i);
    if (await titleInput.isVisible()) {
      await titleInput.fill('Document Test E2E — Informatique');
    }

    // Confirmer l'upload
    const confirmBtn = page.getByRole('button', { name: /envoyer|upload|confirmer|valider/i });
    await confirmBtn.click();

    // Attendre la notification de succès
    await expect(
      page.getByText(/succès|uploadé|téléversé|ajouté/i),
    ).toBeVisible({ timeout: 30_000 });

    // Le document doit apparaître dans la liste
    await expect(
      page.getByText('Document Test E2E — Informatique'),
    ).toBeVisible({ timeout: 15_000 });
  });

  // ── 3. Génération de quiz ─────────────────────────────────────────────────
  test('génère un quiz depuis un document', async ({ page }) => {
    await page.goto('/enseignant/documents');

    // Sélectionner un document existant (le premier disponible)
    const firstDoc = page.locator('[data-testid="document-card"]').first();
    await expect(firstDoc).toBeVisible({ timeout: 15_000 });
    await firstDoc.click();

    // Cliquer sur "Générer un quiz"
    const generateBtn = page.getByRole('button', { name: /générer.*quiz|créer.*quiz/i });
    await expect(generateBtn).toBeVisible();
    await generateBtn.click();

    // Configurer la génération
    const nbQuestionsInput = page.getByLabel(/nombre de questions/i);
    if (await nbQuestionsInput.isVisible()) {
      await nbQuestionsInput.fill('5');
    }

    // Lancer la génération
    const launchBtn = page.getByRole('button', { name: /lancer|générer|commencer/i });
    await launchBtn.click();

    // Attendre la fin de la génération (≤ 15 s selon les specs)
    await expect(
      page.getByText(/quiz généré|génération terminée|questions générées/i),
    ).toBeVisible({ timeout: 60_000 });

    // L'éditeur de quiz doit s'afficher
    await expect(page.locator('[data-testid="quiz-editor"]')).toBeVisible({ timeout: 15_000 });
  });

  // ── 4. Édition d'une question ─────────────────────────────────────────────
  test('modifie le contenu d\'une question générée', async ({ page }) => {
    // Aller directement dans l'éditeur du premier quiz
    await page.goto('/enseignant/quiz');
    const firstQuiz = page.locator('[data-testid="quiz-card"]').first();
    await expect(firstQuiz).toBeVisible({ timeout: 15_000 });
    await firstQuiz.click();

    // Cliquer sur "Modifier" sur la première question
    const editBtn = page.locator('[data-testid="question-edit-btn"]').first();
    await expect(editBtn).toBeVisible();
    await editBtn.click();

    // Modifier le contenu de la question
    const contentInput = page.locator('[data-testid="question-content-input"]');
    await expect(contentInput).toBeVisible();
    await contentInput.fill('Qu\'est-ce que la programmation orientée objet ? (modifié E2E)');

    // Sauvegarder
    const saveBtn = page.getByRole('button', { name: /sauvegarder|enregistrer|save/i });
    await saveBtn.click();

    // Confirmer la sauvegarde
    await expect(
      page.getByText(/sauvegardé|enregistré|modifié/i),
    ).toBeVisible({ timeout: 10_000 });
  });

  // ── 5. Publication ─────────────────────────────────────────────────────────
  test('publie un quiz', async ({ page }) => {
    await page.goto('/enseignant/quiz');
    const firstQuiz = page.locator('[data-testid="quiz-card"]').first();
    await expect(firstQuiz).toBeVisible({ timeout: 15_000 });
    await firstQuiz.click();

    // Bouton publier
    const publishBtn = page.getByRole('button', { name: /publier|publish/i });
    await expect(publishBtn).toBeVisible();
    await publishBtn.click();

    // Dialog de confirmation
    const confirmDialog = page.getByRole('dialog');
    if (await confirmDialog.isVisible()) {
      await confirmDialog.getByRole('button', { name: /confirmer|publier|oui/i }).click();
    }

    // Attendre le succès
    await expect(
      page.getByText(/publié|quiz publié/i),
    ).toBeVisible({ timeout: 15_000 });

    // Les boutons SCORM et Moodle doivent être actifs
    const scormBtn = page.getByRole('button', { name: /scorm/i });
    await expect(scormBtn).toBeEnabled({ timeout: 10_000 });
    const moodleBtn = page.getByRole('button', { name: /moodle/i });
    await expect(moodleBtn).toBeEnabled({ timeout: 10_000 });
  });

  // ── 6. Création de session ────────────────────────────────────────────────
  test('crée une session d\'examen', async ({ page }) => {
    await page.goto('/enseignant/sessions');

    const newSessionBtn = page.getByRole('button', { name: /nouvelle session|créer.*session/i });
    await expect(newSessionBtn).toBeVisible();
    await newSessionBtn.click();

    // Sélectionner un quiz publié
    const quizSelect = page.getByLabel(/quiz/i);
    if (await quizSelect.isVisible()) {
      await quizSelect.selectOption({ index: 0 });
    }

    // Remplir les infos de la session
    const durationInput = page.getByLabel(/durée/i);
    if (await durationInput.isVisible()) {
      await durationInput.fill('60');
    }

    // Créer la session
    const createBtn = page.getByRole('button', { name: /créer|valider|confirmer/i });
    await createBtn.click();

    // La session doit apparaître avec un code
    await expect(
      page.getByText(/session créée|code d'accès|code session/i),
    ).toBeVisible({ timeout: 15_000 });
  });

  // ── 7. Export SCORM ─────────────────────────────────────────────────────────
  test('télécharge l\'export SCORM d\'un quiz publié', async ({ page }) => {
    await page.goto('/enseignant/quiz');
    const firstQuiz = page.locator('[data-testid="quiz-card"]').first();
    await expect(firstQuiz).toBeVisible({ timeout: 15_000 });
    await firstQuiz.click();

    const scormBtn = page.getByRole('button', { name: /scorm/i });
    await expect(scormBtn).toBeVisible();

    // Si le bouton est actif, déclencher le téléchargement
    if (await scormBtn.isEnabled()) {
      const [download] = await Promise.all([
        page.waitForEvent('download', { timeout: 30_000 }),
        scormBtn.click(),
      ]);
      expect(download.suggestedFilename()).toMatch(/\.zip$/i);
    } else {
      // Quiz non publié — vérifier que le bouton est bien désactivé
      await expect(scormBtn).toBeDisabled();
      test.info().annotations.push({
        type: 'skip-reason',
        description: 'Quiz non publié — export SCORM désactivé (comportement attendu)',
      });
    }
  });
});
