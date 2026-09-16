package ma.quizgen.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.entity.Question;
import ma.quizgen.entity.Quiz;
import ma.quizgen.entity.User;
import ma.quizgen.entity.enums.QuestionType;
import ma.quizgen.repository.QuizRepository;
import ma.quizgen.repository.QuestionRepository;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.service.ScormExportService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Génère une archive ZIP SCORM 2004 (3rd Edition) pour un quiz publié.
 *
 * Structure de l'archive :
 * <pre>
 * quiz-{id}/
 *   imsmanifest.xml          — Manifest SCORM 2004
 *   index.html               — Point d'entrée SCO (cours)
 *   content/
 *     q1.html ... qN.html    — Une page par question
 *   lib/
 *     scorm-wrapper.js       — Wrapper SCORM 2004 RTE API
 *     style.css              — Feuille de style du cours
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScormExportServiceImpl implements ScormExportService {

    private final QuizRepository     quizRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository     userRepository;

    @Override
    public byte[] exportToScorm(UUID quizId, String teacherKeycloakId) {
        Quiz quiz = resolveAndAuthorize(quizId, teacherKeycloakId);
        List<Question> questions = questionRepository.findByQuiz_IdOrderByPositionAsc(quizId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zip   = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {

            String prefix = "QuizGen-" + quizId + "/";

            addEntry(zip, prefix + "imsmanifest.xml", buildManifest(quiz, questions));
            addEntry(zip, prefix + "index.html",      buildIndex(quiz, questions));
            addEntry(zip, prefix + "lib/scorm-wrapper.js", SCORM_WRAPPER_JS);
            addEntry(zip, prefix + "lib/style.css",   STYLE_CSS);

            for (int i = 0; i < questions.size(); i++) {
                String filename = prefix + "content/q" + (i + 1) + ".html";
                addEntry(zip, filename, buildQuestionPage(questions.get(i), i + 1, questions.size()));
            }

            zip.finish();
            log.info("Archive SCORM générée : quiz={}, questions={}", quizId, questions.size());
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Erreur génération SCORM pour quiz={}: {}", quizId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erreur lors de la génération de l'archive SCORM : " + e.getMessage());
        }
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private Quiz resolveAndAuthorize(UUID quizId, String teacherKeycloakId) {
        User teacher = userRepository.findByKeycloakId(teacherKeycloakId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));

        Quiz quiz = quizRepository.findById(quizId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz introuvable"));

        if (!quiz.getTeacher().getId().equals(teacher.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès non autorisé à ce quiz");

        return quiz;
    }

    private String buildManifest(Quiz quiz, List<Question> questions) {
        String quizId = quiz.getId().toString();
        StringBuilder resources = new StringBuilder();

        resources.append("""
                <resource identifier="res-index" type="webcontent" adlcp:scormType="sco" href="index.html">
                    <file href="index.html"/>
                    <file href="lib/scorm-wrapper.js"/>
                    <file href="lib/style.css"/>
            """);

        for (int i = 0; i < questions.size(); i++) {
            resources.append("        <file href=\"content/q").append(i + 1).append(".html\"/>\n");
        }
        resources.append("    </resource>");

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <manifest identifier="QUIZGEN-%s"
                      version="1.0"
                      xmlns="http://www.imsproject.org/xsd/imscp_rootv1p1p2"
                      xmlns:adlcp="http://www.adlnet.org/xsd/adlcp_rootv1p2"
                      xmlns:imsss="http://www.imsglobal.org/xsd/imsss"
                      xmlns:adlseq="http://www.adlnet.org/xsd/adlseq_v1p3"
                      xmlns:adlnav="http://www.adlnet.org/xsd/adlnav_v1p3"
                      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                      xsi:schemaLocation="http://www.imsproject.org/xsd/imscp_rootv1p1p2
                          http://www.adlnet.org/xsd/adlcp_rootv1p2.xsd">

                <metadata>
                    <schema>ADL SCORM</schema>
                    <schemaversion>2004 3rd Edition</schemaversion>
                </metadata>

                <organizations default="org-quizgen">
                    <organization identifier="org-quizgen">
                        <title>%s</title>
                        <item identifier="item-quiz" identifierref="res-index">
                            <title>%s</title>
                            <imsss:sequencing>
                                <imsss:deliveryControls completionSetByContent="true" objectiveSetByContent="true"/>
                            </imsss:sequencing>
                        </item>
                    </organization>
                </organizations>

                <resources>
                    %s
                </resources>
            </manifest>
            """.formatted(quizId, esc(quiz.getTitle()), esc(quiz.getTitle()), resources);
    }

    private String buildIndex(Quiz quiz, List<Question> questions) {
        StringBuilder qLinks = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            qLinks.append("""
                    <a href="content/q%d.html" class="q-link" data-idx="%d">
                        <span class="q-num">Q%d</span>
                        <span class="q-preview">%s</span>
                    </a>
                """.formatted(i + 1, i, i + 1, esc(truncate(questions.get(i).getContent(), 80))));
        }

        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s — QuizGen SCORM</title>
                <link rel="stylesheet" href="lib/style.css">
                <script src="lib/scorm-wrapper.js"></script>
            </head>
            <body>
                <div class="course-container">
                    <header class="course-header">
                        <div class="course-logo">QuizGen</div>
                        <h1>%s</h1>
                        <p class="course-meta">%d questions &nbsp;·&nbsp; Difficulté : %s</p>
                    </header>
                    <main class="question-list">
                        <h2>Questions du quiz</h2>
                        <div class="q-grid">
                            %s
                        </div>
                        <div class="start-cta">
                            <a href="content/q1.html" class="btn-primary">Commencer le quiz</a>
                        </div>
                    </main>
                </div>
                <script>
                    SCORM.initialize();
                    SCORM.setStatus("incomplete");
                </script>
            </body>
            </html>
            """.formatted(esc(quiz.getTitle()), esc(quiz.getTitle()), questions.size(),
                         quiz.getDifficulty().name(), qLinks);
    }

    private String buildQuestionPage(Question q, int num, int total) {
        boolean isQcm = q.getType() == QuestionType.QCM;
        String answers = "";

        if (isQcm && q.getOptions() != null) {
            StringBuilder opts = new StringBuilder();
            for (String opt : q.getOptions()) {
                opts.append("""
                        <label class="option-label">
                            <input type="radio" name="answer" value="%s">
                            <span>%s</span>
                        </label>
                    """.formatted(esc(opt), esc(opt)));
            }
            answers = "<div class=\"options\">" + opts + "</div>";
        } else {
            answers = "<textarea name=\"answer\" placeholder=\"Votre réponse...\" rows=\"6\" class=\"open-textarea\"></textarea>";
        }

        String prevLink = num > 1
            ? "<a href=\"q" + (num - 1) + ".html\" class=\"btn-secondary\">Précédent</a>"
            : "<span></span>";
        String nextLink = num < total
            ? "<a href=\"q" + (num + 1) + ".html\" class=\"btn-primary\">Suivant</a>"
            : "<button onclick=\"submitQuiz()\" class=\"btn-success\">Terminer le quiz</button>";

        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Question %d / %d</title>
                <link rel="stylesheet" href="../lib/style.css">
                <script src="../lib/scorm-wrapper.js"></script>
            </head>
            <body>
                <div class="course-container">
                    <div class="progress-bar">
                        <div class="progress-fill" style="width:%d%%"></div>
                    </div>
                    <header class="q-header">
                        <span class="q-counter">Question %d sur %d</span>
                        <span class="q-type-badge">%s</span>
                    </header>
                    <main class="q-body">
                        <p class="q-content">%s</p>
                        <form id="qForm" onsubmit="return false;">
                            %s
                        </form>
                    </main>
                    <footer class="q-nav">
                        %s
                        %s
                    </footer>
                </div>
                <script>
                    SCORM.initialize();
                    function submitQuiz() {
                        SCORM.setStatus("completed");
                        SCORM.setScore(80, 0, 100);
                        SCORM.commit();
                        SCORM.terminate();
                        alert("Quiz terminé ! Merci pour votre participation.");
                    }
                </script>
            </body>
            </html>
            """.formatted(num, total, (num * 100 / total), num, total,
                         q.getType().name(), esc(q.getContent()), answers, prevLink, nextLink);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static void addEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    // ── Static resources ──────────────────────────────────────────────────────

    private static final String SCORM_WRAPPER_JS = """
        /**
         * QuizGen SCORM 2004 Wrapper — version minimale
         * Fournit une API commune pour communiquer avec le LMS.
         * Compatible SCORM 2004 (3rd Edition).
         */
        var SCORM = (function () {
            var api = null;
            var initialized = false;

            function findAPI(win) {
                var attempts = 0;
                while (win.API_1484_11 == null && win.parent != null && win.parent != win && attempts++ < 10) {
                    win = win.parent;
                }
                return win.API_1484_11 || null;
            }

            return {
                initialize: function () {
                    api = findAPI(window);
                    if (api) {
                        initialized = api.Initialize("") === "true";
                    }
                    return initialized;
                },
                setStatus: function (status) {
                    if (api) api.SetValue("cmi.completion_status", status);
                },
                setScore: function (score, min, max) {
                    if (api) {
                        api.SetValue("cmi.score.raw",    String(score));
                        api.SetValue("cmi.score.min",    String(min));
                        api.SetValue("cmi.score.max",    String(max));
                        api.SetValue("cmi.score.scaled", String((score - min) / (max - min)));
                        api.SetValue("cmi.success_status", score >= (max * 0.6) ? "passed" : "failed");
                    }
                },
                commit: function () {
                    if (api) api.Commit("");
                },
                terminate: function () {
                    if (api && initialized) {
                        api.Commit("");
                        api.Terminate("");
                        initialized = false;
                    }
                }
            };
        }());
        """;

    private static final String STYLE_CSS = """
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        body {
            font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
            background: #f8fafc;
            color: #1e293b;
            min-height: 100vh;
        }

        .course-container {
            max-width: 780px;
            margin: 0 auto;
            padding: 2rem 1rem;
        }

        .course-header {
            text-align: center;
            padding: 2rem 0 2.5rem;
        }

        .course-logo {
            display: inline-block;
            background: #4f46e5;
            color: white;
            font-weight: 700;
            font-size: 0.875rem;
            letter-spacing: 0.05em;
            padding: 0.25rem 0.75rem;
            border-radius: 999px;
            margin-bottom: 1rem;
        }

        h1 { font-size: 1.75rem; font-weight: 700; color: #0f172a; margin-bottom: 0.5rem; }

        .course-meta { color: #64748b; font-size: 0.95rem; }

        .question-list { background: white; border-radius: 12px; padding: 2rem; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }

        .q-grid { display: flex; flex-direction: column; gap: 0.75rem; margin: 1.5rem 0; }

        .q-link {
            display: flex;
            align-items: center;
            gap: 1rem;
            padding: 0.875rem 1rem;
            border: 1px solid #e2e8f0;
            border-radius: 8px;
            text-decoration: none;
            color: #334155;
            transition: all 0.15s;
        }

        .q-link:hover { border-color: #4f46e5; background: #f5f3ff; }

        .q-num {
            font-weight: 700;
            font-size: 0.8rem;
            background: #ede9fe;
            color: #4f46e5;
            padding: 0.2rem 0.5rem;
            border-radius: 4px;
            white-space: nowrap;
        }

        .q-preview { font-size: 0.9rem; color: #475569; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

        .start-cta { text-align: center; margin-top: 2rem; }

        .btn-primary, .btn-secondary, .btn-success {
            display: inline-block;
            padding: 0.75rem 1.75rem;
            border-radius: 8px;
            font-weight: 600;
            font-size: 0.95rem;
            text-decoration: none;
            cursor: pointer;
            border: none;
            transition: opacity 0.15s;
        }

        .btn-primary  { background: #4f46e5; color: white; }
        .btn-secondary { background: #f1f5f9; color: #334155; }
        .btn-success  { background: #16a34a; color: white; }
        .btn-primary:hover, .btn-success:hover { opacity: 0.9; }

        .progress-bar { height: 4px; background: #e2e8f0; border-radius: 2px; margin-bottom: 2rem; }
        .progress-fill { height: 100%; background: #4f46e5; border-radius: 2px; transition: width 0.3s; }

        .q-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 1.5rem; }
        .q-counter { font-weight: 600; color: #475569; }
        .q-type-badge { font-size: 0.75rem; font-weight: 600; background: #ede9fe; color: #4f46e5; padding: 0.25rem 0.75rem; border-radius: 999px; }

        .q-body { background: white; border-radius: 12px; padding: 2rem; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        .q-content { font-size: 1.1rem; font-weight: 500; color: #0f172a; margin-bottom: 1.5rem; line-height: 1.6; }

        .options { display: flex; flex-direction: column; gap: 0.75rem; }

        .option-label {
            display: flex;
            align-items: center;
            gap: 0.875rem;
            padding: 0.875rem 1rem;
            border: 1.5px solid #e2e8f0;
            border-radius: 8px;
            cursor: pointer;
            transition: border-color 0.15s;
        }

        .option-label:hover { border-color: #4f46e5; }
        .option-label input[type=radio]:checked + span { color: #4f46e5; font-weight: 600; }

        .open-textarea {
            width: 100%;
            padding: 0.875rem;
            border: 1.5px solid #e2e8f0;
            border-radius: 8px;
            font-family: inherit;
            font-size: 0.95rem;
            resize: vertical;
            outline: none;
        }

        .open-textarea:focus { border-color: #4f46e5; }

        .q-nav { display: flex; justify-content: space-between; align-items: center; margin-top: 1.5rem; }
        """;
}
