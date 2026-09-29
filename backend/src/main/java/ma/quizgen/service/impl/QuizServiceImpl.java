package ma.quizgen.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.dto.*;
import ma.quizgen.entity.Document;
import ma.quizgen.entity.Question;
import ma.quizgen.entity.Quiz;
import ma.quizgen.entity.User;
import ma.quizgen.entity.enums.AttemptStatus;
import ma.quizgen.entity.enums.Difficulty;
import ma.quizgen.entity.enums.QuestionType;
import ma.quizgen.entity.enums.QuizStatus;
import ma.quizgen.entity.enums.Role;
import ma.quizgen.repository.AttemptRepository;
import ma.quizgen.repository.SessionRepository;
import ma.quizgen.repository.DocumentRepository;
import ma.quizgen.repository.QuestionRepository;
import ma.quizgen.repository.QuizRepository;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.service.NlpServiceClient;
import ma.quizgen.service.QuizService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizServiceImpl implements QuizService {

    private final QuizRepository      quizRepository;
    private final QuestionRepository  questionRepository;
    private final DocumentRepository  documentRepository;
    private final UserRepository      userRepository;
    private final NlpServiceClient    nlpClient;
    private final AttemptRepository   attemptRepository;
    private final SessionRepository   sessionRepository;

    /**
     * Au-delà de ce délai, une génération sans résultat est considérée perdue
     * (tâche tuée, résultat Celery expiré, worker redémarré…) et le quiz passe
     * en FAILED — sinon il resterait DRAFT indéfiniment et bloquerait le document.
     * Doit couvrir l'attente en file + le time limit Celery (≈ 15 + 21 min).
     */
    @Value("${nlp.service.generation-timeout-minutes:40}")
    private long generationTimeoutMinutes = 40;

    // ── helpers ───────────────────────────────────────────────────────────────

    private User resolveUser(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Utilisateur introuvable (keycloakId=" + keycloakId + ")"));
    }

    // ── API ───────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public QuizDto create(QuizCreateRequest request, String teacherKeycloakId) {
        User teacher  = resolveUser(teacherKeycloakId);
        Document doc  = documentRepository.findById(request.documentId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable"));

        if (!doc.getUser().getId().equals(teacher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce document ne vous appartient pas");
        }

        ensureNoGenerationInProgress(doc);

        List<QuestionType> types = (request.questionTypes() != null && !request.questionTypes().isEmpty())
            ? request.questionTypes()
            : List.of(QuestionType.QCM, QuestionType.OUVERTE);

        // Persist quiz in DRAFT before calling NLP (to have an id in case of failure)
        Quiz quiz = quizRepository.save(Quiz.builder()
            .document(doc)
            .teacher(teacher)
            .title(request.title())
            .description(request.description())
            .difficulty(request.difficulty())
            .nbQuestions(request.nbQuestions())
            .questionTypes(joinTypes(types))
            .quizStatus(QuizStatus.DRAFT)
            .build());

        return QuizDto.from(submitGeneration(quiz));
    }

    @Override
    @Transactional
    public QuizDto regenerate(UUID quizId, String teacherKeycloakId) {
        Quiz quiz = findOwnedQuiz(quizId, teacherKeycloakId);
        if (quiz.getQuizStatus() != QuizStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Seul un quiz dont la génération a échoué peut être relancé");
        }
        ensureNoGenerationInProgress(quiz.getDocument());

        quiz.setQuizStatus(QuizStatus.DRAFT);
        return QuizDto.from(submitGeneration(quiz));
    }

    /**
     * Empêche de soumettre plusieurs générations NLP concurrentes pour le même
     * document (double-clic, nouvel essai pendant qu'une génération tourne) —
     * chaque tâche est coûteuse et fait concurrence aux autres sur le worker.
     * Les générations en cours sont d'abord rafraîchies : une génération
     * terminée ou perdue ne doit pas bloquer une nouvelle demande.
     */
    private void ensureNoGenerationInProgress(Document doc) {
        quizRepository.findByDocument_IdAndQuizStatusAndNlpTaskIdIsNotNull(doc.getId(), QuizStatus.DRAFT)
            .forEach(this::advanceIfNlpDone);

        if (quizRepository.existsByDocument_IdAndQuizStatusAndNlpTaskIdIsNotNull(doc.getId(), QuizStatus.DRAFT)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Une génération est déjà en cours pour ce document. Veuillez patienter qu'elle se termine.");
        }
    }

    /**
     * Soumission asynchrone uniquement : la génération peut prendre plusieurs
     * minutes. Le résultat est récupéré via le polling de GET /quizzes/{id}
     * (voir advanceIfNlpDone) — bloquer la requête HTTP ferait attendre
     * l'enseignant sans retour.
     */
    private Quiz submitGeneration(Quiz quiz) {
        Document doc = quiz.getDocument();
        NlpGenerateRequest nlpReq = new NlpGenerateRequest(
            doc.getId().toString(),
            doc.getBucketName(),
            doc.getObjectKey(),
            quiz.getNbQuestions(),
            parseTypes(quiz.getQuestionTypes()),
            quiz.getDifficulty()
        );
        try {
            String taskId = nlpClient.submitGeneration(nlpReq);
            quiz.setNlpTaskId(taskId);
            quiz.setGenerationStartedAt(LocalDateTime.now());
            quiz = quizRepository.save(quiz);
            log.info("Quiz {} — génération NLP soumise (task_id={})", quiz.getId(), taskId);
            return quiz;
        } catch (Exception e) {
            log.error("Échec de soumission NLP pour quiz {}: {}", quiz.getId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Impossible de soumettre la génération NLP : " + e.getMessage());
        }
    }

    private static String joinTypes(List<QuestionType> types) {
        return String.join(",", types.stream().map(Enum::name).toList());
    }

    /** Types mémorisés du quiz ; défaut QCM + OUVERTE pour les quiz créés avant leur mémorisation. */
    private static List<QuestionType> parseTypes(String stored) {
        if (stored == null || stored.isBlank()) return List.of(QuestionType.QCM, QuestionType.OUVERTE);
        return Arrays.stream(stored.split(",")).map(String::trim).map(QuestionType::valueOf).toList();
    }

    private Quiz findOwnedQuiz(UUID quizId, String teacherKeycloakId) {
        User teacher = resolveUser(teacherKeycloakId);
        Quiz quiz = quizRepository.findByIdWithQuestions(quizId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz introuvable"));
        if (!quiz.getTeacher().getId().equals(teacher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non autorisé");
        }
        return quiz;
    }

    private static boolean isGenerating(Quiz quiz) {
        return quiz.getQuizStatus() == QuizStatus.DRAFT && quiz.getNlpTaskId() != null;
    }

    @Override
    @Transactional
    public QuizDto getById(UUID quizId, String requesterKeycloakId) {
        User requester = resolveUser(requesterKeycloakId);
        Quiz quiz = quizRepository.findByIdWithQuestions(quizId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz introuvable"));

        boolean isOwner = quiz.getTeacher().getId().equals(requester.getId());
        if (isOwner || requester.getRole() == Role.ADMIN) {
            if (quiz.getQuizStatus() == QuizStatus.DRAFT && quiz.getNlpTaskId() != null) {
                quiz = advanceIfNlpDone(quiz);
            }
            return QuizDto.from(quiz);
        }

        // Étudiant : uniquement un quiz d'une session qu'il a rejointe, et
        // sans les réponses tant que sa tentative n'est pas soumise.
        if (requester.getRole() != Role.ETUDIANT
                || !attemptRepository.existsBySession_Quiz_IdAndStudent_Id(quizId, requester.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès à ce quiz non autorisé");
        }
        QuizDto dto = QuizDto.from(quiz);
        boolean hasSubmitted = attemptRepository.existsBySession_Quiz_IdAndStudent_IdAndAttemptStatus(
            quizId, requester.getId(), AttemptStatus.SUBMITTED);
        return hasSubmitted ? dto : dto.withoutAnswers();
    }

    /**
     * Vérifie l'état de la tâche NLP associée à un quiz en DRAFT et le complète
     * si la génération est terminée (succès ou échec), ou le passe en FAILED si
     * elle dépasse le délai maximal. Appelé à chaque poll de GET /quizzes/{id}
     * et avant toute nouvelle génération sur le même document.
     */
    private Quiz advanceIfNlpDone(Quiz quiz) {
        NlpTaskStatus status;
        try {
            status = nlpClient.pollStatus(quiz.getNlpTaskId());
        } catch (Exception e) {
            log.warn("Statut NLP indisponible pour quiz {} (task_id={}): {}",
                quiz.getId(), quiz.getNlpTaskId(), e.getMessage());
            return isGenerationOverdue(quiz) ? markFailed(quiz, "service NLP injoignable") : quiz;
        }

        if (status.isSuccess()) {
            List<Question> questions = mapNlpToQuestions(status.result(), quiz);
            if (questions.isEmpty()) {
                return markFailed(quiz, "aucune question exploitable générée");
            }
            quiz.getQuestions().addAll(questions);
            quiz.setQuizStatus(QuizStatus.REVIEWING);
            quiz = quizRepository.save(quiz);
            log.info("Quiz {} généré ({} questions)", quiz.getId(), quiz.getQuestions().size());
        } else if (status.isFailure()) {
            quiz = markFailed(quiz, status.status() + ": " + status.error());
        } else if (isGenerationOverdue(quiz)) {
            quiz = markFailed(quiz, "délai dépassé (statut " + status.status() + ")");
        }
        return quiz;
    }

    private boolean isGenerationOverdue(Quiz quiz) {
        // Depuis la dernière soumission (relance comprise) ; created_at pour les anciens quiz
        LocalDateTime startedAt = quiz.getGenerationStartedAt() != null
            ? quiz.getGenerationStartedAt() : quiz.getCreatedAt();
        return startedAt != null
            && startedAt.plusMinutes(generationTimeoutMinutes).isBefore(LocalDateTime.now());
    }

    private Quiz markFailed(Quiz quiz, String reason) {
        log.error("Génération NLP échouée pour quiz {} (task_id={}): {}",
            quiz.getId(), quiz.getNlpTaskId(), reason);
        quiz.setQuizStatus(QuizStatus.FAILED);
        return quizRepository.save(quiz);
    }

    @Override
    @Transactional
    public PageResponse<QuizDto> listForTeacher(String teacherKeycloakId, Pageable pageable) {
        User teacher = resolveUser(teacherKeycloakId);
        Page<Quiz> page = quizRepository.findByTeacher_IdOrderByCreatedAtDesc(teacher.getId(), pageable);
        // Les générations en cours sont rafraîchies ici aussi : sans cela, un quiz
        // dont l'enseignant a quitté la page de génération resterait « Brouillon »
        // dans la liste tant que personne ne l'ouvre.
        page.getContent().stream().filter(QuizServiceImpl::isGenerating).forEach(this::advanceIfNlpDone);
        return PageResponse.of(page.map(QuizDto::from));
    }

    @Override
    @Transactional
    public QuizDto updateQuestions(UUID quizId, QuizUpdateQuestionsRequest request, String teacherKeycloakId) {
        Quiz quiz = findOwnedQuiz(quizId, teacherKeycloakId);

        if (quiz.getQuizStatus() == QuizStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un quiz archivé ne peut pas être modifié");
        }
        if (isGenerating(quiz)) {
            // Les questions générées seraient ajoutées par-dessus l'édition manuelle
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Génération en cours : attendez sa fin (ou son échec) avant de modifier les questions");
        }

        questionRepository.deleteByQuiz_Id(quizId);
        quiz.getQuestions().clear();

        List<Question> newQuestions = new ArrayList<>();
        for (int i = 0; i < request.questions().size(); i++) {
            QuestionUpdateRequest qr = request.questions().get(i);
            validateQcm(qr);
            newQuestions.add(Question.builder()
                .quiz(quiz)
                .type(qr.type())
                .content(qr.content())
                .options(qr.options())
                .correctAnswer(qr.correctAnswer())
                .explanation(qr.explanation())
                .difficulty(qr.difficulty() != null ? qr.difficulty() : quiz.getDifficulty())
                .keywords(qr.keywords())
                .position(qr.position() != null ? qr.position() : i)
                .build());
        }

        quiz.getQuestions().addAll(newQuestions);
        quiz.setQuizStatus(request.publish() ? QuizStatus.PUBLISHED : QuizStatus.REVIEWING);
        quiz = quizRepository.save(quiz);

        log.info("Quiz {} mis à jour ({} questions, publié={})", quizId, newQuestions.size(), request.publish());
        return QuizDto.from(quiz);
    }

    @Override
    @Transactional
    public void delete(UUID quizId, String teacherKeycloakId) {
        Quiz quiz = findOwnedQuiz(quizId, teacherKeycloakId);
        // La suppression d'un quiz supprime en cascade ses sessions et les
        // copies des étudiants : on la refuse plutôt que de perdre des résultats.
        if (sessionRepository.existsByQuiz_Id(quizId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Ce quiz est utilisé par des sessions : il ne peut pas être supprimé");
        }
        // Une génération en cours est annulée : elle consommerait sinon des
        // appels LLM (payants en mode en ligne) pour un quiz qui n'existe plus.
        if (isGenerating(quiz)) {
            nlpClient.cancelGeneration(quiz.getNlpTaskId());
        }
        quizRepository.delete(quiz);
        log.info("Quiz {} supprimé (statut {})", quizId, quiz.getQuizStatus());
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private List<Question> mapNlpToQuestions(List<Map<String, Object>> raw, Quiz quiz) {
        if (raw == null || raw.isEmpty()) return List.of();
        List<Question> list = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            Map<String, Object> item = raw.get(i);
            try {
                list.add(Question.builder()
                    .quiz(quiz)
                    .type(QuestionType.valueOf(str(item, "type")))
                    .content(str(item, "content"))
                    .options(castList(item.get("options")))
                    .correctAnswer(str(item, "correct_answer"))
                    .explanation(str(item, "explanation"))
                    .difficulty(parseDifficulty(str(item, "difficulty")))
                    .keywords(castList(item.get("keywords")))
                    .position(i)
                    .build());
            } catch (Exception e) {
                log.warn("Question NLP ignorée (index {}): {}", i, e.getMessage());
            }
        }
        return list;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v != null ? v.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object o) {
        return (o instanceof List<?>) ? (List<String>) o : null;
    }

    private static Difficulty parseDifficulty(String s) {
        try { return Difficulty.valueOf(s); } catch (Exception e) { return Difficulty.MOYEN; }
    }

    private void validateQcm(QuestionUpdateRequest qr) {
        if (qr.type() != QuestionType.QCM) return;
        if (qr.options() == null || qr.options().size() != 4)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un QCM doit avoir exactement 4 options");
        boolean valid = qr.options().stream().anyMatch(o -> o.equalsIgnoreCase(qr.correctAnswer()));
        if (!valid)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La bonne réponse doit être l'une des options du QCM");
    }
}
