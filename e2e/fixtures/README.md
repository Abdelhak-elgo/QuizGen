# Fixtures E2E

Ce dossier contient les fichiers de test utilisés par les tests Playwright.

## Contenu attendu

- `sample.pdf` — PDF de test pour tester l'upload et la génération de quiz.
  Doit contenir au moins 2 pages de contenu textuel en français.
  **Non versionné** (ajouter à `.gitignore`).
  À créer avec :
  ```bash
  # Python — créer un PDF de test minimaliste
  python3 -c "
  import fpdf
  pdf = fpdf.FPDF()
  pdf.add_page()
  pdf.set_font('Helvetica', size=12)
  pdf.multi_cell(0, 10, '''
  Introduction à la Programmation Orientée Objet

  La programmation orientée objet (POO) est un paradigme de programmation
  qui organise le code en objets. Les objets encapsulent des données (attributs)
  et des comportements (méthodes).

  Les quatre piliers de la POO sont :
  1. Encapsulation — regrouper données et méthodes dans un objet
  2. Héritage — réutiliser le code via des hiérarchies de classes
  3. Polymorphisme — même interface, comportements différents
  4. Abstraction — masquer la complexité interne

  Python est un langage qui supporte pleinement la POO grâce aux classes.
  ''')
  pdf.output('e2e/fixtures/sample.pdf')
  " 2>/dev/null || echo 'fpdf non installé — créer le PDF manuellement'
  ```

## Variables d'environnement requises

Copier et adapter :
```bash
# e2e/.env.test
BASE_URL=http://localhost:80/frontend
KC_URL=http://localhost:8180
KC_REALM=quizgen
TEST_TEACHER_USER=prof.test@quizgen.ma
TEST_TEACHER_PASS=Test1234!
TEST_STUDENT_USER=etudiant.test@quizgen.ma
TEST_STUDENT_PASS=Test1234!
TEST_SESSION_CODE=TEST01
```
