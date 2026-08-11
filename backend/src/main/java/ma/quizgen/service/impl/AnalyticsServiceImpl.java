package ma.quizgen.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.dto.QuizAnalyticsDto;
import ma.quizgen.dto.QuizAnalyticsDto.QuestionStatDto;
import ma.quizgen.dto.StudentDashboardDto;
import ma.quizgen.dto.StudentDashboardDto.AttemptSummaryDto;
import ma.quizgen.entity.Attempt;
import ma.quizgen.entity.Question;
import ma.quizgen.entity.Quiz;
import ma.quizgen.entity.User;
import ma.quizgen.entity.enums.AttemptStatus;
import ma.quizgen.entity.enums.QuestionType;
import ma.quizgen.repository.AttemptRepository;
import ma.quizgen.repository.QuizRepository;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.service.AnalyticsService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsServiceImpl implements AnalyticsService {

    private final QuizRepository    quizRepository;
    private final AttemptRepository attemptRepository;
    private final UserRepository    userRepository;

    private static final double PASSING_THRESHOLD = 60.0;

    private User resolveUser(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
    }

    @Override
    @Transactional(readOnly = true)
    public QuizAnalyticsDto getQuizAnalytics(UUID quizId, String teacherKeycloakId) {
        User teacher = resolveUser(teacherKeycloakId);
        Quiz quiz = quizRepository.findByIdWithQuestions(quizId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz introuvable"));

        if (!quiz.getTeacher().getId().equals(teacher.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non autorisé");

        long total   = attemptRepository.countSubmittedAttemptsByQuizId(quizId);
        Double avg   = attemptRepository.findAverageScoreByQuizId(quizId);
        long passing = attemptRepository.countPassingAttemptsByQuizId(quizId, PASSING_THRESHOLD);

        double successRate = total > 0 ? Math.round(passing * 100.0 / total * 10.0) / 10.0 : 0.0;
        double avgScore    = avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0;

        List<QuestionStatDto> questionStats = buildQuestionStats(quiz, quizId);

        return new QuizAnalyticsDto(quizId, quiz.getTitle(), total, avgScore, successRate, questionStats);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDashboardDto getStudentDashboard(String studentKeycloakId) {
        User student = resolveUser(studentKeycloakId);

        List<Attempt> allAttempts = attemptRepository
            .findByStudent_IdOrderByStartedAtDesc(student.getId(), PageRequest.ofSize(100))
            .getContent();

        List<Attempt> submitted = allAttempts.stream()
            .filter(a -> a.getAttemptStatus() == AttemptStatus.SUBMITTED)
            .toList();

        double avg = submitted.stream()
            .filter(a -> a.getMaxScore() != null && a.getMaxScore() > 0)
            .mapToDouble(a -> a.getScore() * 100.0 / a.getMaxScore())
            .average()
            .orElse(0.0);

        List<AttemptSummaryDto> recent = allAttempts.stream()
            .limit(10)
            .map(a -> new AttemptSummaryDto(
                a.getId(),
                a.getSession().getId(),
                a.getSession().getQuiz().getTitle(),
                a.getScore(),
                a.getMaxScore(),
                (a.getMaxScore() != null && a.getMaxScore() > 0)
                    ? Math.round(a.getScore() * 100.0 / a.getMaxScore() * 10.0) / 10.0 : 0.0,
                a.getAttemptStatus().name(),
                a.getCompletedAt()
            ))
            .toList();

        return new StudentDashboardDto(
            student.getId(),
            student.getFirstName() + " " + student.getLastName(),
            allAttempts.size(),
            Math.round(avg * 10.0) / 10.0,
            recent
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Calcule les statistiques par question à partir des tentatives soumises.
     * Pour chaque question, compte combien de fois la bonne réponse a été donnée.
     */
    private List<QuestionStatDto> buildQuestionStats(Quiz quiz, UUID quizId) {
        List<Question> questions = quiz.getQuestions();
        if (questions == null || questions.isEmpty()) return List.of();

        // Récupérer toutes les tentatives soumises pour ce quiz (via la relation session→quiz)
        List<Attempt> attempts = attemptRepository.findSubmittedByQuizId(quizId);
        List<QuestionStatDto> stats = new ArrayList<>();
        for (Question q : questions) {
            long total   = 0;
            long correct = 0;

            // Count correct answers across all submitted attempts
            // attempts is loaded per-session; here we compute stats from available data
            for (Attempt a : attempts) {
                if (a.getAttemptStatus() != AttemptStatus.SUBMITTED || a.getAnswers() == null) continue;
                String given = a.getAnswers().get(q.getId().toString());
                if (given != null) {
                    total++;
                    if (q.getType() == QuestionType.QCM
                            && given.trim().equalsIgnoreCase(q.getCorrectAnswer().trim())) {
                        correct++;
                    }
                }
            }

            double rate = total > 0 ? Math.round(correct * 100.0 / total * 10.0) / 10.0 : 0.0;
            String preview = q.getContent().length() > 80
                ? q.getContent().substring(0, 80) + "…" : q.getContent();
            stats.add(new QuestionStatDto(q.getId(), preview, total, correct, rate));
        }
        return stats;
    }
}
