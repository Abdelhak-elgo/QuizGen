package ma.quizgen.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.dto.AttemptReviewDto;
import ma.quizgen.dto.AttemptReviewDto.QuestionReview;
import ma.quizgen.dto.NlpRecommendationRequest;
import ma.quizgen.dto.RecommendationDto;
import ma.quizgen.dto.SessionResultsDto;
import ma.quizgen.dto.SessionResultsDto.StudentResult;
import ma.quizgen.dto.SessionResultsDto.StudentStatus;
import ma.quizgen.dto.SessionResultsDto.Summary;
import ma.quizgen.entity.Attempt;
import ma.quizgen.entity.Question;
import ma.quizgen.entity.Session;
import ma.quizgen.entity.User;
import ma.quizgen.entity.enums.AttemptStatus;
import ma.quizgen.entity.enums.Role;
import ma.quizgen.repository.AttemptRepository;
import ma.quizgen.repository.QuestionRepository;
import ma.quizgen.repository.SessionRepository;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.service.NlpServiceClient;
import ma.quizgen.service.ResultsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResultsServiceImpl implements ResultsService {

    /** Seuil de réussite d'une copie, en % (aligné sur AnalyticsServiceImpl). */
    static final double PASSING_PERCENT = 60.0;

    private final SessionRepository  sessionRepository;
    private final AttemptRepository  attemptRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository     userRepository;
    private final NlpServiceClient   nlpClient;

    // ── Résultats de session ──────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public SessionResultsDto sessionResults(UUID sessionId, String teacherKeycloakId) {
        Session session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session introuvable"));
        ensureTeacherOf(session, teacherKeycloakId);

        Map<UUID, Attempt> attemptsByStudent = new LinkedHashMap<>();
        attemptRepository.findBySession_Id(sessionId)
            .forEach(a -> attemptsByStudent.put(a.getStudent().getId(), a));

        // Assignés d'abord (même sans tentative), puis les étudiants ayant rejoint librement
        Map<UUID, StudentResult> rows = new LinkedHashMap<>();
        session.getParticipants().forEach(s -> rows.put(s.getId(), toRow(s, true, attemptsByStudent.get(s.getId()))));
        attemptsByStudent.values().forEach(a ->
            rows.putIfAbsent(a.getStudent().getId(), toRow(a.getStudent(), false, a)));

        List<StudentResult> students = rows.values().stream()
            .sorted(Comparator.comparing(StudentResult::lastName, Comparator.nullsLast(String::compareToIgnoreCase)))
            .toList();

        return new SessionResultsDto(
            session.getId(),
            session.getQuiz().getId(),
            session.getQuiz().getTitle(),
            session.getQuiz().getNbQuestions(),
            session.getSessionStatus(),
            session.getStartTime(),
            session.getEndTime(),
            summarize(students),
            students
        );
    }

    private static StudentResult toRow(User student, boolean assigned, Attempt attempt) {
        if (attempt == null) {
            return new StudentResult(student.getId(), student.getFirstName(), student.getLastName(),
                student.getEmail(), assigned, StudentStatus.NOT_STARTED,
                null, null, null, null, null, null, false);
        }
        return new StudentResult(student.getId(), student.getFirstName(), student.getLastName(),
            student.getEmail(), assigned, StudentStatus.valueOf(attempt.getAttemptStatus().name()),
            attempt.getId(), attempt.getScore(), attempt.getMaxScore(), percent(attempt),
            attempt.getStartedAt(), attempt.getCompletedAt(), attempt.getRecommendation() != null);
    }

    private static Summary summarize(List<StudentResult> students) {
        List<Double> submitted = students.stream()
            .filter(s -> s.status() == StudentStatus.SUBMITTED && s.percent() != null)
            .map(StudentResult::percent)
            .toList();
        Double average = submitted.isEmpty() ? null
            : round1(submitted.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        Double passRate = submitted.isEmpty() ? null
            : round1(submitted.stream().filter(p -> p >= PASSING_PERCENT).count() * 100.0 / submitted.size());
        return new Summary(
            students.size(),
            count(students, StudentStatus.NOT_STARTED),
            count(students, StudentStatus.IN_PROGRESS),
            count(students, StudentStatus.SUBMITTED),
            average,
            passRate
        );
    }

    private static int count(List<StudentResult> students, StudentStatus status) {
        return (int) students.stream().filter(s -> s.status() == status).count();
    }

    // ── Copie corrigée ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public AttemptReviewDto attemptReview(UUID attemptId, String teacherKeycloakId) {
        Attempt attempt = findAttemptForTeacher(attemptId, teacherKeycloakId);
        return toReview(attempt, gradeQuestions(attempt));
    }

    private List<QuestionReview> gradeQuestions(Attempt attempt) {
        Map<String, String> answers = attempt.getAnswers() == null ? Map.of() : attempt.getAnswers();
        List<Question> questions = questionRepository
            .findByQuiz_IdOrderByPositionAsc(attempt.getSession().getQuiz().getId());
        return questions.stream().map(q -> {
            String answer = answers.get(q.getId().toString());
            AnswerGrader.Grade grade = AnswerGrader.grade(q, answer, attempt.getBertScoreDetails());
            return new QuestionReview(q.getId(), q.getPosition(), q.getType(), q.getContent(), q.getOptions(),
                q.getCorrectAnswer(), answer, grade.outcome(), grade.partialScore(), q.getExplanation());
        }).toList();
    }

    private static AttemptReviewDto toReview(Attempt attempt, List<QuestionReview> questions) {
        User student = attempt.getStudent();
        return new AttemptReviewDto(
            attempt.getId(),
            attempt.getSession().getId(),
            attempt.getSession().getQuiz().getId(),
            attempt.getSession().getQuiz().getTitle(),
            student.getId(),
            (nullToEmpty(student.getFirstName()) + " " + nullToEmpty(student.getLastName())).trim(),
            student.getEmail(),
            attempt.getAttemptStatus(),
            attempt.getScore(),
            attempt.getMaxScore(),
            percent(attempt),
            attempt.getStartedAt(),
            attempt.getCompletedAt(),
            questions,
            RecommendationDto.fromStored(attempt.getRecommendation(), attempt.getRecommendationGeneratedAt())
        );
    }

    // ── Recommandation ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public RecommendationDto generateRecommendation(UUID attemptId, String teacherKeycloakId) {
        Attempt attempt = findAttemptForTeacher(attemptId, teacherKeycloakId);
        if (attempt.getAttemptStatus() != AttemptStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "La recommandation n'est disponible qu'une fois la copie soumise");
        }

        NlpRecommendationRequest request = toNlpRequest(attempt, gradeQuestions(attempt));
        Map<String, Object> response;
        try {
            response = nlpClient.requestRecommendation(request);
        } catch (IllegalStateException e) {
            log.warn("Recommandation impossible pour la tentative {} : {}", attemptId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Recommandation indisponible : " + e.getMessage());
        }

        LocalDateTime now = LocalDateTime.now();
        RecommendationDto recommendation = RecommendationDto.fromNlp(response, now);
        attempt.setRecommendation(recommendation.toStored());
        attempt.setRecommendationGeneratedAt(now);
        attemptRepository.save(attempt);
        log.info("Recommandation générée pour la tentative {} ({})", attemptId, recommendation.model());
        return recommendation;
    }

    /** Copie anonymisée : ni nom ni e-mail de l'étudiant ne sont envoyés au LLM. */
    private static NlpRecommendationRequest toNlpRequest(Attempt attempt, List<QuestionReview> questions) {
        List<NlpRecommendationRequest.Item> items = questions.stream()
            .map(q -> new NlpRecommendationRequest.Item(
                q.content(), q.type(), q.correctAnswer(), q.studentAnswer(), q.outcome(), q.explanation()))
            .toList();
        int maxScore = attempt.getMaxScore() != null && attempt.getMaxScore() > 0 ? attempt.getMaxScore() : items.size();
        return new NlpRecommendationRequest(
            attempt.getSession().getQuiz().getTitle(),
            attempt.getScore() == null ? 0 : attempt.getScore(),
            maxScore,
            items
        );
    }

    // ── Accès ─────────────────────────────────────────────────────────────────

    private Attempt findAttemptForTeacher(UUID attemptId, String teacherKeycloakId) {
        Attempt attempt = attemptRepository.findById(attemptId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Copie introuvable"));
        ensureTeacherOf(attempt.getSession(), teacherKeycloakId);
        return attempt;
    }

    /** Seuls l'enseignant de la session et les administrateurs voient les copies. */
    private void ensureTeacherOf(Session session, String keycloakId) {
        User requester = userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
        boolean isOwner = session.getTeacher().getId().equals(requester.getId());
        if (!isOwner && requester.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non autorisé");
        }
    }

    // ── Utilitaires ───────────────────────────────────────────────────────────

    private static Double percent(Attempt attempt) {
        if (attempt.getAttemptStatus() != AttemptStatus.SUBMITTED
                || attempt.getMaxScore() == null || attempt.getMaxScore() == 0) {
            return null;
        }
        return round1(attempt.getScore() * 100.0 / attempt.getMaxScore());
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
