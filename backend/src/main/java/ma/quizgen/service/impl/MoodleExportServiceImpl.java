package ma.quizgen.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.entity.Question;
import ma.quizgen.entity.Quiz;
import ma.quizgen.entity.User;
import ma.quizgen.entity.enums.QuestionType;
import ma.quizgen.repository.QuestionRepository;
import ma.quizgen.repository.QuizRepository;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.service.MoodleExportService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Génère un fichier XML au format Moodle Quiz Question Format.
 *
 * Compatible Moodle 4.x — importable via :
 * Administration du cours → Banque de questions → Importer → Format Moodle XML
 *
 * Types supportés :
 * - QCM      → &lt;question type="multichoice"&gt; (choix unique, note incorrecte −33 ou −25%)
 * - OUVERTE  → &lt;question type="essay"&gt;
 * - EXERCICE → &lt;question type="shortanswer"&gt;
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MoodleExportServiceImpl implements MoodleExportService {

    private final QuizRepository     quizRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository     userRepository;

    @Override
    public byte[] exportToMoodleXml(UUID quizId, String teacherKeycloakId) {
        User teacher = userRepository.findByKeycloakId(teacherKeycloakId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));

        Quiz quiz = quizRepository.findById(quizId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz introuvable"));

        if (!quiz.getTeacher().getId().equals(teacher.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès non autorisé à ce quiz");

        List<Question> questions = questionRepository.findByQuiz_IdOrderByPositionAsc(quizId);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<quiz>\n");
        xml.append(buildCategoryQuestion(quiz));

        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            xml.append(buildQuestion(q, i + 1));
        }

        xml.append("</quiz>\n");

        log.info("Export Moodle XML généré : quiz={}, {} questions", quizId, questions.size());
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── Question builders ─────────────────────────────────────────────────────

    private String buildCategoryQuestion(Quiz quiz) {
        return """
              <question type="category">
                <category>
                  <text>$module$/QuizGen/%s</text>
                </category>
                <info format="html">
                  <text>Quiz généré par QuizGen — Difficulté : %s — %d questions</text>
                </info>
              </question>
            """.formatted(esc(quiz.getTitle()), quiz.getDifficulty().name(), quiz.getNbQuestions());
    }

    private String buildQuestion(Question q, int position) {
        return switch (q.getType()) {
            case QCM      -> buildMultiChoice(q, position);
            case OUVERTE  -> buildEssay(q, position);
            case EXERCICE -> buildShortAnswer(q, position);
        };
    }

    private String buildMultiChoice(Question q, int position) {
        List<String> options = q.getOptions();
        if (options == null || options.isEmpty()) return buildShortAnswer(q, position);

        int nbOptions = options.size();
        // Pénalité par défaut pour une mauvaise réponse (format Moodle : négatif = pénalité)
        double penalty = nbOptions > 0 ? Math.round(-100.0 / (nbOptions - 1) * 100.0) / 100.0 : -33.33;

        StringBuilder answers = new StringBuilder();
        for (String opt : options) {
            boolean isCorrect = opt.trim().equalsIgnoreCase(q.getCorrectAnswer().trim());
            double fraction   = isCorrect ? 100.0 : penalty;
            String feedback   = isCorrect ? "Bonne réponse !" : "Mauvaise réponse.";

            answers.append("""
                    <answer fraction="%s" format="html">
                      <text><![CDATA[%s]]></text>
                      <feedback format="html"><text>%s</text></feedback>
                    </answer>
                """.formatted(formatFraction(fraction), opt, feedback));
        }

        return """
              <question type="multichoice">
                <name><text>Q%d — %s</text></name>
                <questiontext format="html">
                  <text><![CDATA[<p>%s</p>]]></text>
                </questiontext>
                <generalfeedback format="html">
                  <text><![CDATA[<p><strong>Explication :</strong> %s</p>]]></text>
                </generalfeedback>
                <defaultgrade>1</defaultgrade>
                <penalty>0.3333333</penalty>
                <hidden>0</hidden>
                <idnumber></idnumber>
                <single>true</single>
                <shuffleanswers>true</shuffleanswers>
                <answernumbering>ABCD</answernumbering>
                <correctfeedback format="html"><text>Bonne réponse !</text></correctfeedback>
                <partiallycorrectfeedback format="html"><text></text></partiallycorrectfeedback>
                <incorrectfeedback format="html"><text>Mauvaise réponse.</text></incorrectfeedback>
                %s
              </question>
            """.formatted(position, esc(truncate(q.getContent(), 50)),
                         esc(q.getContent()), esc(q.getExplanation() != null ? q.getExplanation() : ""),
                         answers);
    }

    private String buildEssay(Question q, int position) {
        return """
              <question type="essay">
                <name><text>Q%d — %s</text></name>
                <questiontext format="html">
                  <text><![CDATA[<p>%s</p>]]></text>
                </questiontext>
                <generalfeedback format="html">
                  <text><![CDATA[<p><strong>Réponse attendue :</strong> %s</p>%s]]></text>
                </generalfeedback>
                <defaultgrade>1</defaultgrade>
                <penalty>0</penalty>
                <hidden>0</hidden>
                <idnumber></idnumber>
                <responseformat>editor</responseformat>
                <responserequired>0</responserequired>
                <responsefieldlines>8</responsefieldlines>
                <minwordlimit></minwordlimit>
                <maxwordlimit></maxwordlimit>
                <attachments>0</attachments>
                <attachmentsrequired>0</attachmentsrequired>
                <graderinfo format="html">
                  <text><![CDATA[<p>Corrigé avec BERTScore (RoBERTa-large). Seuil de validation : F1 ≥ 0.70</p>]]></text>
                </graderinfo>
                <responsetemplate format="html"><text></text></responsetemplate>
              </question>
            """.formatted(position, esc(truncate(q.getContent(), 50)),
                         esc(q.getContent()), esc(q.getCorrectAnswer()),
                         q.getExplanation() != null
                             ? "<p><strong>Explication :</strong> " + esc(q.getExplanation()) + "</p>"
                             : "");
    }

    private String buildShortAnswer(Question q, int position) {
        return """
              <question type="shortanswer">
                <name><text>Q%d — %s</text></name>
                <questiontext format="html">
                  <text><![CDATA[<p>%s</p>]]></text>
                </questiontext>
                <generalfeedback format="html">
                  <text><![CDATA[%s]]></text>
                </generalfeedback>
                <defaultgrade>1</defaultgrade>
                <penalty>0</penalty>
                <hidden>0</hidden>
                <idnumber></idnumber>
                <usecase>0</usecase>
                <answer fraction="100" format="moodle_auto_format">
                  <text>%s</text>
                  <feedback format="html"><text>Bonne réponse !</text></feedback>
                </answer>
                <answer fraction="0" format="moodle_auto_format">
                  <text>*</text>
                  <feedback format="html"><text>Mauvaise réponse.</text></feedback>
                </answer>
              </question>
            """.formatted(position, esc(truncate(q.getContent(), 50)),
                         esc(q.getContent()),
                         q.getExplanation() != null
                             ? "<p><strong>Explication :</strong> " + esc(q.getExplanation()) + "</p>"
                             : "",
                         esc(q.getCorrectAnswer()));
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private static String formatFraction(double f) {
        if (f == Math.floor(f)) return String.valueOf((long) f);
        return String.format("%.5f", f);
    }
}
