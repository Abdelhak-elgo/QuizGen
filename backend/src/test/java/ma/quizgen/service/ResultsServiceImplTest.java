package ma.quizgen.service;

import ma.quizgen.dto.AttemptReviewDto;
import ma.quizgen.dto.NlpRecommendationRequest;
import ma.quizgen.dto.RecommendationDto;
import ma.quizgen.dto.SessionResultsDto;
import ma.quizgen.dto.SessionResultsDto.StudentStatus;
import ma.quizgen.entity.*;
import ma.quizgen.entity.enums.*;
import ma.quizgen.repository.AttemptRepository;
import ma.quizgen.repository.QuestionRepository;
import ma.quizgen.repository.SessionRepository;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.service.impl.ResultsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResultsServiceImpl — résultats, copies et recommandations")
class ResultsServiceImplTest {

    @Mock SessionRepository  sessionRepository;
    @Mock AttemptRepository  attemptRepository;
    @Mock QuestionRepository questionRepository;
    @Mock UserRepository     userRepository;
    @Mock NlpServiceClient   nlpClient;

    @InjectMocks ResultsServiceImpl resultsService;

    private static final String TEACHER_KC = "kc-teacher";

    private User teacher;
    private User alice;
    private User bob;
    private User carl;
    private Quiz quiz;
    private Session session;
    private Question qcm;
    private Question open;

    @BeforeEach
    void setUp() {
        teacher = User.builder().id(UUID.randomUUID()).keycloakId(TEACHER_KC).role(Role.ENSEIGNANT).build();
        alice = student("Alice", "Amrani");
        bob   = student("Bob", "Bennani");
        carl  = student("Carl", "Chraibi");

        quiz = Quiz.builder().id(UUID.randomUUID()).teacher(teacher).title("Urbanisation")
            .difficulty(Difficulty.MOYEN).nbQuestions(2).quizStatus(QuizStatus.PUBLISHED).build();
        session = Session.builder().id(UUID.randomUUID()).quiz(quiz).teacher(teacher)
            .startTime(LocalDateTime.now().minusHours(1)).endTime(LocalDateTime.now().plusHours(1))
            .sessionStatus(SessionStatus.OPEN).build();

        qcm = Question.builder().id(UUID.randomUUID()).quiz(quiz).type(QuestionType.QCM)
            .content("Qu'est-ce qu'un SI ?").options(List.of("A", "B", "C", "D")).correctAnswer("A")
            .explanation("Cours 1").position(0).build();
        open = Question.builder().id(UUID.randomUUID()).quiz(quiz).type(QuestionType.OUVERTE)
            .content("Définir la gouvernance.").correctAnswer("Pilotage du SI").position(1).build();
    }

    private static User student(String first, String last) {
        return User.builder().id(UUID.randomUUID()).firstName(first).lastName(last)
            .email(first.toLowerCase() + "@univ.ma").role(Role.ETUDIANT).build();
    }

    private Attempt submitted(User student, int score, Map<String, String> answers, Map<String, Object> bert) {
        return Attempt.builder().id(UUID.randomUUID()).session(session).student(student)
            .answers(answers).bertScoreDetails(bert).score(score).maxScore(2)
            .attemptStatus(AttemptStatus.SUBMITTED).completedAt(LocalDateTime.now()).build();
    }

    // ── sessionResults ────────────────────────────────────────────────────────

    @Test
    @DisplayName("sessionResults : assignés sans copie + étudiants libres, avec statut et synthèse")
    void sessionResults_listsAssignedAndWalkInStudents() {
        session.getParticipants().addAll(Set.of(alice, bob));
        Attempt aliceAttempt = submitted(alice, 2, Map.of(), null);
        Attempt carlAttempt = Attempt.builder().id(UUID.randomUUID()).session(session).student(carl)
            .attemptStatus(AttemptStatus.IN_PROGRESS).build();

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(userRepository.findByKeycloakId(TEACHER_KC)).thenReturn(Optional.of(teacher));
        when(attemptRepository.findBySession_Id(session.getId())).thenReturn(List.of(aliceAttempt, carlAttempt));

        SessionResultsDto result = resultsService.sessionResults(session.getId(), TEACHER_KC);

        assertThat(result.students()).extracting(SessionResultsDto.StudentResult::lastName)
            .containsExactly("Amrani", "Bennani", "Chraibi");
        assertThat(result.students()).extracting(SessionResultsDto.StudentResult::status)
            .containsExactly(StudentStatus.SUBMITTED, StudentStatus.NOT_STARTED, StudentStatus.IN_PROGRESS);
        assertThat(result.students().get(2).assigned()).isFalse();
        assertThat(result.students().get(0).percent()).isEqualTo(100.0);
        assertThat(result.summary().submitted()).isEqualTo(1);
        assertThat(result.summary().averagePercent()).isEqualTo(100.0);
        assertThat(result.summary().passRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("sessionResults : 403 pour un autre enseignant")
    void sessionResults_forbiddenForOtherTeacher() {
        User other = User.builder().id(UUID.randomUUID()).keycloakId("kc-other").role(Role.ENSEIGNANT).build();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(userRepository.findByKeycloakId("kc-other")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> resultsService.sessionResults(session.getId(), "kc-other"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Non autorisé");
    }

    @Test
    @DisplayName("sessionResults : pas de moyenne sans copie soumise")
    void sessionResults_noAverageWithoutSubmissions() {
        session.getParticipants().add(alice);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(userRepository.findByKeycloakId(TEACHER_KC)).thenReturn(Optional.of(teacher));
        when(attemptRepository.findBySession_Id(session.getId())).thenReturn(List.of());

        SessionResultsDto result = resultsService.sessionResults(session.getId(), TEACHER_KC);

        assertThat(result.summary().averagePercent()).isNull();
        assertThat(result.summary().notStarted()).isEqualTo(1);
    }

    // ── attemptReview ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("attemptReview : verdict par question (QCM exact, ouverte via BERTScore)")
    void attemptReview_gradesEachQuestion() {
        Map<String, Object> bert = Map.of(open.getId().toString(), Map.of("label", "PARTIEL", "partialScore", 0.5));
        Attempt attempt = submitted(alice, 1,
            Map.of(qcm.getId().toString(), "a", open.getId().toString(), "Gestion du SI"), bert);

        when(attemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(userRepository.findByKeycloakId(TEACHER_KC)).thenReturn(Optional.of(teacher));
        when(questionRepository.findByQuiz_IdOrderByPositionAsc(quiz.getId())).thenReturn(List.of(qcm, open));

        AttemptReviewDto review = resultsService.attemptReview(attempt.getId(), TEACHER_KC);

        assertThat(review.questions()).extracting(AttemptReviewDto.QuestionReview::outcome)
            .containsExactly(AnswerOutcome.CORRECT, AnswerOutcome.PARTIAL);
        assertThat(review.questions().get(1).partialScore()).isEqualTo(0.5);
        assertThat(review.studentName()).isEqualTo("Alice Amrani");
        assertThat(review.recommendation()).isNull();
    }

    @Test
    @DisplayName("attemptReview : réponse vide = UNANSWERED, ouverte sans BERTScore = UNGRADED")
    void attemptReview_unansweredAndUngraded() {
        Attempt attempt = submitted(alice, 0, Map.of(open.getId().toString(), "Une réponse"), null);

        when(attemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(userRepository.findByKeycloakId(TEACHER_KC)).thenReturn(Optional.of(teacher));
        when(questionRepository.findByQuiz_IdOrderByPositionAsc(quiz.getId())).thenReturn(List.of(qcm, open));

        AttemptReviewDto review = resultsService.attemptReview(attempt.getId(), TEACHER_KC);

        assertThat(review.questions()).extracting(AttemptReviewDto.QuestionReview::outcome)
            .containsExactly(AnswerOutcome.UNANSWERED, AnswerOutcome.UNGRADED);
    }

    // ── generateRecommendation ────────────────────────────────────────────────

    @Test
    @DisplayName("generateRecommendation : copie anonymisée envoyée au LLM, diagnostic enregistré")
    void generateRecommendation_sendsAnonymousCopyAndStoresResult() {
        Attempt attempt = submitted(alice, 1, Map.of(qcm.getId().toString(), "B"), null);
        when(attemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(userRepository.findByKeycloakId(TEACHER_KC)).thenReturn(Optional.of(teacher));
        when(questionRepository.findByQuiz_IdOrderByPositionAsc(quiz.getId())).thenReturn(List.of(qcm, open));
        when(nlpClient.requestRecommendation(any())).thenReturn(Map.of(
            "summary", "Confusion sur la définition du SI.",
            "weaknesses", List.of("Q1 : définition du SI"),
            "focus_areas", List.of("Définition et composantes d'un SI"),
            "actions", List.of("Relire le chapitre 1"),
            "model", "claude-haiku-4-5-20251001"));

        RecommendationDto rec = resultsService.generateRecommendation(attempt.getId(), TEACHER_KC);

        ArgumentCaptor<NlpRecommendationRequest> sent = ArgumentCaptor.forClass(NlpRecommendationRequest.class);
        verify(nlpClient).requestRecommendation(sent.capture());
        assertThat(sent.getValue().items()).extracting(NlpRecommendationRequest.Item::outcome)
            .containsExactly(AnswerOutcome.INCORRECT, AnswerOutcome.UNANSWERED);
        assertThat(sent.getValue().toString()).doesNotContain("Alice", "alice@univ.ma");

        assertThat(rec.focusAreas()).containsExactly("Définition et composantes d'un SI");
        assertThat(attempt.getRecommendation()).containsEntry("summary", "Confusion sur la définition du SI.");
        assertThat(attempt.getRecommendationGeneratedAt()).isNotNull();
        verify(attemptRepository).save(attempt);
    }

    @Test
    @DisplayName("generateRecommendation : 409 si la copie n'est pas soumise")
    void generateRecommendation_requiresSubmittedAttempt() {
        Attempt inProgress = Attempt.builder().id(UUID.randomUUID()).session(session).student(alice)
            .attemptStatus(AttemptStatus.IN_PROGRESS).build();
        when(attemptRepository.findById(inProgress.getId())).thenReturn(Optional.of(inProgress));
        when(userRepository.findByKeycloakId(TEACHER_KC)).thenReturn(Optional.of(teacher));

        assertThatThrownBy(() -> resultsService.generateRecommendation(inProgress.getId(), TEACHER_KC))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("soumise");
        verifyNoInteractions(nlpClient);
    }

    @Test
    @DisplayName("generateRecommendation : 503 avec le message du LLM si le service échoue")
    void generateRecommendation_surfacesNlpError() {
        Attempt attempt = submitted(alice, 1, Map.of(), null);
        when(attemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(userRepository.findByKeycloakId(TEACHER_KC)).thenReturn(Optional.of(teacher));
        when(questionRepository.findByQuiz_IdOrderByPositionAsc(quiz.getId())).thenReturn(List.of(qcm));
        when(nlpClient.requestRecommendation(any())).thenThrow(new IllegalStateException("clé API invalide"));

        assertThatThrownBy(() -> resultsService.generateRecommendation(attempt.getId(), TEACHER_KC))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("clé API invalide");
        verify(attemptRepository, never()).save(any());
    }
}
