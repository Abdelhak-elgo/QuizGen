package ma.quizgen.service;

import ma.quizgen.dto.AttemptDto;
import ma.quizgen.dto.SessionCreateRequest;
import ma.quizgen.dto.SessionDto;
import ma.quizgen.entity.*;
import ma.quizgen.entity.enums.*;
import ma.quizgen.repository.*;
import ma.quizgen.service.impl.SessionServiceImpl;
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
@DisplayName("SessionServiceImpl — tests unitaires")
class SessionServiceImplTest {

    @Mock SessionRepository sessionRepository;
    @Mock QuizRepository    quizRepository;
    @Mock UserRepository    userRepository;
    @Mock AttemptRepository attemptRepository;

    @InjectMocks SessionServiceImpl sessionService;

    private User teacher;
    private User student;
    private Quiz publishedQuiz;
    private final String TEACHER_KC = "kc-teacher";
    private final String STUDENT_KC = "kc-student";

    @BeforeEach
    void setUp() {
        teacher = User.builder().id(UUID.randomUUID()).keycloakId(TEACHER_KC)
            .firstName("Ahmed").lastName("Prof").build();
        student = User.builder().id(UUID.randomUUID()).keycloakId(STUDENT_KC)
            .firstName("Sara").lastName("Étudiant").build();

        Document doc = Document.builder().id(UUID.randomUUID()).user(teacher)
            .bucketName("documents").objectKey("doc.pdf").build();

        publishedQuiz = Quiz.builder()
            .id(UUID.randomUUID()).teacher(teacher).document(doc)
            .title("Quiz IA").difficulty(Difficulty.MOYEN).nbQuestions(5)
            .quizStatus(QuizStatus.PUBLISHED).build();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create : crée une session SCHEDULED si startTime dans le futur")
    void create_scheduled() {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        LocalDateTime end   = LocalDateTime.now().plusHours(3);
        SessionCreateRequest req = new SessionCreateRequest(publishedQuiz.getId(), start, end, "CODE123");

        Session session = Session.builder()
            .id(UUID.randomUUID()).quiz(publishedQuiz).teacher(teacher)
            .startTime(start).endTime(end).accessCode("CODE123")
            .sessionStatus(SessionStatus.SCHEDULED).build();

        when(userRepository.findByKeycloakId(TEACHER_KC)).thenReturn(Optional.of(teacher));
        when(quizRepository.findById(publishedQuiz.getId())).thenReturn(Optional.of(publishedQuiz));
        when(sessionRepository.save(any())).thenReturn(session);

        SessionDto result = sessionService.create(req, TEACHER_KC);

        assertThat(result.sessionStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(result.accessCode()).isEqualTo("CODE123");
    }

    @Test
    @DisplayName("create : lève 409 si le quiz n'est pas publié")
    void create_quizNotPublished() {
        publishedQuiz.setQuizStatus(QuizStatus.REVIEWING);
        SessionCreateRequest req = new SessionCreateRequest(
            publishedQuiz.getId(),
            LocalDateTime.now().plusHours(1),
            LocalDateTime.now().plusHours(3),
            null
        );

        when(userRepository.findByKeycloakId(TEACHER_KC)).thenReturn(Optional.of(teacher));
        when(quizRepository.findById(publishedQuiz.getId())).thenReturn(Optional.of(publishedQuiz));

        assertThatThrownBy(() -> sessionService.create(req, TEACHER_KC))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("publiés");
    }

    @Test
    @DisplayName("create : lève 400 si endTime avant startTime")
    void create_invalidTimeRange() {
        SessionCreateRequest req = new SessionCreateRequest(
            publishedQuiz.getId(),
            LocalDateTime.now().plusHours(3),
            LocalDateTime.now().plusHours(1),  // end avant start
            null
        );

        when(userRepository.findByKeycloakId(TEACHER_KC)).thenReturn(Optional.of(teacher));
        when(quizRepository.findById(publishedQuiz.getId())).thenReturn(Optional.of(publishedQuiz));

        assertThatThrownBy(() -> sessionService.create(req, TEACHER_KC))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("fin");
    }

    // ── join ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("join : étudiant rejoint une session ouverte sans code")
    void join_success() {
        Session openSession = Session.builder()
            .id(UUID.randomUUID()).quiz(publishedQuiz).teacher(teacher)
            .startTime(LocalDateTime.now().minusHours(1))
            .endTime(LocalDateTime.now().plusHours(2))
            .sessionStatus(SessionStatus.OPEN).build();

        Attempt attempt = Attempt.builder()
            .id(UUID.randomUUID()).session(openSession).student(student)
            .score(0).maxScore(5).attemptStatus(AttemptStatus.IN_PROGRESS).build();

        when(sessionRepository.findById(openSession.getId())).thenReturn(Optional.of(openSession));
        when(attemptRepository.findBySession_IdAndStudent_Id(openSession.getId(), student.getId()))
            .thenReturn(Optional.empty());
        when(userRepository.findByKeycloakId(STUDENT_KC)).thenReturn(Optional.of(student));
        when(attemptRepository.save(any())).thenReturn(attempt);

        AttemptDto result = sessionService.join(openSession.getId(), null, STUDENT_KC);

        assertThat(result.attemptStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("join : lève 403 si le code d'accès est incorrect")
    void join_wrongAccessCode() {
        Session session = Session.builder()
            .id(UUID.randomUUID()).quiz(publishedQuiz).teacher(teacher)
            .startTime(LocalDateTime.now().minusHours(1))
            .endTime(LocalDateTime.now().plusHours(2))
            .accessCode("SECRET")
            .sessionStatus(SessionStatus.OPEN).build();

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.join(session.getId(), "WRONG", STUDENT_KC))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Code d'accès");
    }

    @Test
    @DisplayName("join : idempotent — retourne l'attempt existant")
    void join_idempotent() {
        Session openSession = Session.builder()
            .id(UUID.randomUUID()).quiz(publishedQuiz).teacher(teacher)
            .startTime(LocalDateTime.now().minusHours(1))
            .endTime(LocalDateTime.now().plusHours(2))
            .sessionStatus(SessionStatus.OPEN).build();

        Attempt existing = Attempt.builder()
            .id(UUID.randomUUID()).session(openSession).student(student)
            .score(0).maxScore(5).attemptStatus(AttemptStatus.IN_PROGRESS).build();

        when(sessionRepository.findById(openSession.getId())).thenReturn(Optional.of(openSession));
        when(attemptRepository.findBySession_IdAndStudent_Id(openSession.getId(), student.getId()))
            .thenReturn(Optional.of(existing));

        AttemptDto result = sessionService.join(openSession.getId(), null, STUDENT_KC);

        assertThat(result.id()).isEqualTo(existing.getId());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("join : lève 409 si la session est terminée")
    void join_sessionClosed() {
        Session closed = Session.builder()
            .id(UUID.randomUUID()).quiz(publishedQuiz).teacher(teacher)
            .startTime(LocalDateTime.now().minusHours(3))
            .endTime(LocalDateTime.now().minusHours(1))
            .sessionStatus(SessionStatus.CLOSED).build();

        when(sessionRepository.findById(closed.getId())).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> sessionService.join(closed.getId(), null, STUDENT_KC))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("terminée");
    }
}
