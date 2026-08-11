package ma.quizgen.service;

import ma.quizgen.dto.AttemptDto;
import ma.quizgen.dto.SubmitAnswersRequest;
import ma.quizgen.entity.*;
import ma.quizgen.entity.enums.*;
import ma.quizgen.repository.AttemptRepository;
import ma.quizgen.repository.QuestionRepository;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.service.impl.AttemptServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
@DisplayName("AttemptServiceImpl — tests unitaires")
class AttemptServiceImplTest {

    @Mock AttemptRepository  attemptRepository;
    @Mock QuestionRepository questionRepository;
    @Mock UserRepository     userRepository;

    @InjectMocks AttemptServiceImpl attemptService;

    private User student;
    private User teacher;
    private Quiz quiz;
    private Session session;
    private Attempt attempt;
    private Question qcm;
    private final String STUDENT_KC = "kc-student";

    @BeforeEach
    void setUp() {
        teacher = User.builder().id(UUID.randomUUID()).keycloakId("kc-teacher").build();
        student = User.builder().id(UUID.randomUUID()).keycloakId(STUDENT_KC)
            .firstName("Sara").lastName("Elamrani").build();

        Document doc = Document.builder().id(UUID.randomUUID()).user(teacher)
            .bucketName("documents").objectKey("doc.pdf").build();

        quiz = Quiz.builder().id(UUID.randomUUID()).teacher(teacher).document(doc)
            .title("Quiz IA").difficulty(Difficulty.MOYEN).nbQuestions(1)
            .quizStatus(QuizStatus.PUBLISHED).build();

        session = Session.builder().id(UUID.randomUUID()).quiz(quiz).teacher(teacher)
            .startTime(LocalDateTime.now().minusHours(1))
            .endTime(LocalDateTime.now().plusHours(2))
            .sessionStatus(SessionStatus.OPEN).build();

        qcm = Question.builder()
            .id(UUID.randomUUID()).quiz(quiz)
            .type(QuestionType.QCM)
            .content("Qu'est-ce que l'IA ?")
            .options(List.of("Machine learning", "Base de données", "OS", "Langage"))
            .correctAnswer("Machine learning")
            .difficulty(Difficulty.MOYEN)
            .position(0).build();

        attempt = Attempt.builder()
            .id(UUID.randomUUID()).session(session).student(student)
            .score(0).maxScore(1)
            .attemptStatus(AttemptStatus.IN_PROGRESS).build();
    }

    // ── submit ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("submit : bonne réponse QCM → score = 1")
    void submit_correctAnswer() {
        Map<String, String> answers = Map.of(qcm.getId().toString(), "Machine learning");
        SubmitAnswersRequest req = new SubmitAnswersRequest(answers);

        when(userRepository.findByKeycloakId(STUDENT_KC)).thenReturn(Optional.of(student));
        when(attemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(questionRepository.findByQuiz_IdOrderByPositionAsc(quiz.getId())).thenReturn(List.of(qcm));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AttemptDto result = attemptService.submit(attempt.getId(), req, STUDENT_KC);

        assertThat(result.score()).isEqualTo(1);
        assertThat(result.maxScore()).isEqualTo(1);
        assertThat(result.attemptStatus()).isEqualTo(AttemptStatus.SUBMITTED);
    }

    @Test
    @DisplayName("submit : mauvaise réponse QCM → score = 0")
    void submit_wrongAnswer() {
        Map<String, String> answers = Map.of(qcm.getId().toString(), "OS");
        SubmitAnswersRequest req = new SubmitAnswersRequest(answers);

        when(userRepository.findByKeycloakId(STUDENT_KC)).thenReturn(Optional.of(student));
        when(attemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(questionRepository.findByQuiz_IdOrderByPositionAsc(quiz.getId())).thenReturn(List.of(qcm));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AttemptDto result = attemptService.submit(attempt.getId(), req, STUDENT_KC);

        assertThat(result.score()).isEqualTo(0);
        assertThat(result.attemptStatus()).isEqualTo(AttemptStatus.SUBMITTED);
    }

    @Test
    @DisplayName("submit : comparaison insensible à la casse")
    void submit_caseInsensitive() {
        Map<String, String> answers = Map.of(qcm.getId().toString(), "MACHINE LEARNING");
        when(userRepository.findByKeycloakId(STUDENT_KC)).thenReturn(Optional.of(student));
        when(attemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(questionRepository.findByQuiz_IdOrderByPositionAsc(quiz.getId())).thenReturn(List.of(qcm));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AttemptDto result = attemptService.submit(attempt.getId(), new SubmitAnswersRequest(answers), STUDENT_KC);
        assertThat(result.score()).isEqualTo(1);
    }

    @Test
    @DisplayName("submit : lève 409 si déjà soumis")
    void submit_alreadySubmitted() {
        attempt.setAttemptStatus(AttemptStatus.SUBMITTED);

        when(userRepository.findByKeycloakId(STUDENT_KC)).thenReturn(Optional.of(student));
        when(attemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> attemptService.submit(
                attempt.getId(), new SubmitAnswersRequest(Map.of()), STUDENT_KC))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("déjà été soumise");
    }

    @Test
    @DisplayName("submit : lève 409 si le temps de la session est écoulé")
    void submit_sessionExpired() {
        session.setEndTime(LocalDateTime.now().minusMinutes(5));

        when(userRepository.findByKeycloakId(STUDENT_KC)).thenReturn(Optional.of(student));
        when(attemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> attemptService.submit(
                attempt.getId(), new SubmitAnswersRequest(Map.of()), STUDENT_KC))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("écoulé");

        assertThat(attempt.getAttemptStatus()).isEqualTo(AttemptStatus.EXPIRED);
    }

    @Test
    @DisplayName("submit : lève 403 si l'étudiant n'est pas le propriétaire de l'attempt")
    void submit_forbidden() {
        User otherStudent = User.builder().id(UUID.randomUUID()).keycloakId("kc-other").build();

        when(userRepository.findByKeycloakId("kc-other")).thenReturn(Optional.of(otherStudent));
        when(attemptRepository.findById(attempt.getId())).thenReturn(Optional.of(attempt));

        assertThatThrownBy(() -> attemptService.submit(
                attempt.getId(), new SubmitAnswersRequest(Map.of()), "kc-other"))
            .isInstanceOf(ResponseStatusException.class);
    }
}
