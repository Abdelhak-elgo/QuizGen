package ma.quizgen.service;

import ma.quizgen.dto.*;
import ma.quizgen.entity.*;
import ma.quizgen.entity.enums.*;
import ma.quizgen.repository.*;
import ma.quizgen.service.impl.QuizServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuizServiceImpl — tests unitaires")
class QuizServiceImplTest {

    @Mock QuizRepository      quizRepository;
    @Mock QuestionRepository  questionRepository;
    @Mock DocumentRepository  documentRepository;
    @Mock UserRepository      userRepository;
    @Mock NlpServiceClient    nlpClient;
    @Mock AttemptRepository   attemptRepository;
    @Mock SessionRepository   sessionRepository;

    @InjectMocks QuizServiceImpl quizService;

    private User teacher;
    private Document document;
    private final String TEACHER_KC_ID = "kc-teacher-uuid";

    @BeforeEach
    void setUp() {
        teacher = User.builder()
            .id(UUID.randomUUID())
            .keycloakId(TEACHER_KC_ID)
            .email("prof@univ.ma")
            .firstName("Ahmed")
            .lastName("Enseignant")
            .role(Role.ENSEIGNANT)
            .build();

        document = Document.builder()
            .id(UUID.randomUUID())
            .user(teacher)
            .originalFilename("cours-ia.pdf")
            .bucketName("documents")
            .objectKey("documents/cours-ia.pdf")
            .build();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create : soumet la génération NLP en asynchrone et retourne immédiatement")
    void create_success() {
        QuizCreateRequest req = new QuizCreateRequest(
            document.getId(), "Quiz IA", "Description", Difficulty.MOYEN, 5, null);

        Quiz savedQuiz = Quiz.builder()
            .id(UUID.randomUUID())
            .document(document)
            .teacher(teacher)
            .title("Quiz IA")
            .difficulty(Difficulty.MOYEN)
            .nbQuestions(5)
            .quizStatus(QuizStatus.DRAFT)
            .build();

        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(quizRepository.existsByDocument_IdAndQuizStatusAndNlpTaskIdIsNotNull(document.getId(), QuizStatus.DRAFT))
            .thenReturn(false);
        when(quizRepository.save(any())).thenReturn(savedQuiz);
        when(nlpClient.submitGeneration(any())).thenReturn("task-abc");

        QuizDto result = quizService.create(req, TEACHER_KC_ID);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Quiz IA");
        assertThat(result.quizStatus()).isEqualTo(QuizStatus.DRAFT);
        verify(nlpClient).submitGeneration(any(NlpGenerateRequest.class));
        verify(nlpClient, never()).submitAndWait(any());
    }

    @Test
    @DisplayName("create : lève 409 si une génération est déjà en cours pour ce document")
    void create_conflictWhenAlreadyGenerating() {
        QuizCreateRequest req = new QuizCreateRequest(
            document.getId(), "Quiz IA", null, Difficulty.MOYEN, 5, null);

        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(quizRepository.existsByDocument_IdAndQuizStatusAndNlpTaskIdIsNotNull(document.getId(), QuizStatus.DRAFT))
            .thenReturn(true);

        assertThatThrownBy(() -> quizService.create(req, TEACHER_KC_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("déjà en cours");

        verifyNoInteractions(nlpClient);
        verify(quizRepository, never()).save(any());
    }

    @Test
    @DisplayName("create : lève 403 si le document appartient à un autre utilisateur")
    void create_forbidden_document() {
        User otherUser = User.builder()
            .id(UUID.randomUUID())
            .keycloakId("other-kc")
            .build();
        Document docOther = Document.builder()
            .id(UUID.randomUUID())
            .user(otherUser)
            .bucketName("documents")
            .objectKey("other.pdf")
            .build();

        QuizCreateRequest req = new QuizCreateRequest(
            docOther.getId(), "Quiz", null, Difficulty.MOYEN, 5, null);

        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(documentRepository.findById(docOther.getId())).thenReturn(Optional.of(docOther));

        assertThatThrownBy(() -> quizService.create(req, TEACHER_KC_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("appartient");
    }

    @Test
    @DisplayName("create : lève 503 si la soumission au microservice NLP échoue")
    void create_nlpFailure() {
        QuizCreateRequest req = new QuizCreateRequest(
            document.getId(), "Quiz", null, Difficulty.MOYEN, 5, null);

        Quiz draft = Quiz.builder()
            .id(UUID.randomUUID()).document(document).teacher(teacher)
            .title("Quiz").difficulty(Difficulty.MOYEN).nbQuestions(5)
            .quizStatus(QuizStatus.DRAFT).build();

        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(quizRepository.existsByDocument_IdAndQuizStatusAndNlpTaskIdIsNotNull(document.getId(), QuizStatus.DRAFT))
            .thenReturn(false);
        when(quizRepository.save(any())).thenReturn(draft);
        when(nlpClient.submitGeneration(any())).thenThrow(new IllegalStateException("Microservice injoignable"));

        assertThatThrownBy(() -> quizService.create(req, TEACHER_KC_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("NLP");
    }

    // ── getById (polling / avancement NLP) ───────────────────────────────────────

    @Test
    @DisplayName("getById : complète le quiz quand la tâche NLP est SUCCESS")
    void getById_completesQuiz_onNlpSuccess() {
        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        Quiz quiz = Quiz.builder()
            .id(UUID.randomUUID()).document(document).teacher(teacher)
            .title("Quiz IA").difficulty(Difficulty.MOYEN).nbQuestions(1)
            .quizStatus(QuizStatus.DRAFT).nlpTaskId("task-abc").build();

        NlpTaskStatus success = new NlpTaskStatus(
            "task-abc", "SUCCESS", 100,
            List.of(Map.of(
                "type", "QCM",
                "content", "Qu'est-ce que l'IA ?",
                "options", List.of("Machine learning", "Base de données", "Langage", "OS"),
                "correct_answer", "Machine learning",
                "explanation", "Explication",
                "difficulty", "MOYEN",
                "keywords", List.of("IA")
            )),
            null
        );

        when(quizRepository.findByIdWithQuestions(quiz.getId())).thenReturn(Optional.of(quiz));
        when(nlpClient.pollStatus("task-abc")).thenReturn(success);
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuizDto result = quizService.getById(quiz.getId(), TEACHER_KC_ID);

        assertThat(result.quizStatus()).isEqualTo(QuizStatus.REVIEWING);
        assertThat(result.questions()).hasSize(1);
    }

    @Test
    @DisplayName("getById : passe le quiz en FAILED quand la tâche NLP échoue")
    void getById_marksFailed_onNlpFailure() {
        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        Quiz quiz = Quiz.builder()
            .id(UUID.randomUUID()).document(document).teacher(teacher)
            .title("Quiz IA").difficulty(Difficulty.MOYEN).nbQuestions(1)
            .quizStatus(QuizStatus.DRAFT).nlpTaskId("task-abc").build();

        NlpTaskStatus failure = new NlpTaskStatus("task-abc", "FAILURE", null, null, "Timeout");

        when(quizRepository.findByIdWithQuestions(quiz.getId())).thenReturn(Optional.of(quiz));
        when(nlpClient.pollStatus("task-abc")).thenReturn(failure);
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuizDto result = quizService.getById(quiz.getId(), TEACHER_KC_ID);

        assertThat(result.quizStatus()).isEqualTo(QuizStatus.FAILED);
    }

    private Quiz draftQuiz(LocalDateTime createdAt) {
        return Quiz.builder()
            .id(UUID.randomUUID()).document(document).teacher(teacher)
            .title("Quiz IA").difficulty(Difficulty.MOYEN).nbQuestions(1)
            .quizStatus(QuizStatus.DRAFT).nlpTaskId("task-abc").createdAt(createdAt)
            .questions(new ArrayList<>()).build();
    }

    private void stubTeacherGetting(Quiz quiz, NlpTaskStatus status) {
        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(quizRepository.findByIdWithQuestions(quiz.getId())).thenReturn(Optional.of(quiz));
        when(nlpClient.pollStatus("task-abc")).thenReturn(status);
    }

    @Test
    @DisplayName("getById : passe en FAILED une tâche encore PENDING au-delà du délai (résultat perdu)")
    void getById_marksFailed_whenOverdue() {
        Quiz quiz = draftQuiz(LocalDateTime.now().minusHours(2));
        stubTeacherGetting(quiz, new NlpTaskStatus("task-abc", "PENDING", 0, null, null));
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(quizService.getById(quiz.getId(), TEACHER_KC_ID).quizStatus()).isEqualTo(QuizStatus.FAILED);
    }

    @Test
    @DisplayName("getById : laisse en DRAFT une tâche PENDING récente")
    void getById_keepsDraft_whenPendingWithinTimeout() {
        Quiz quiz = draftQuiz(LocalDateTime.now().minusMinutes(2));
        stubTeacherGetting(quiz, new NlpTaskStatus("task-abc", "PENDING", 0, null, null));

        assertThat(quizService.getById(quiz.getId(), TEACHER_KC_ID).quizStatus()).isEqualTo(QuizStatus.DRAFT);
        verify(quizRepository, never()).save(any());
    }

    @Test
    @DisplayName("getById : REVOKED (tâche expirée en file) => FAILED")
    void getById_marksFailed_onRevoked() {
        Quiz quiz = draftQuiz(LocalDateTime.now());
        stubTeacherGetting(quiz, new NlpTaskStatus("task-abc", "REVOKED", null, null, null));
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(quizService.getById(quiz.getId(), TEACHER_KC_ID).quizStatus()).isEqualTo(QuizStatus.FAILED);
    }

    @Test
    @DisplayName("getById : SUCCESS sans question exploitable => FAILED, pas de quiz vide en révision")
    void getById_marksFailed_whenSuccessWithoutQuestions() {
        Quiz quiz = draftQuiz(LocalDateTime.now());
        stubTeacherGetting(quiz, new NlpTaskStatus("task-abc", "SUCCESS", 100, List.of(), null));
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(quizService.getById(quiz.getId(), TEACHER_KC_ID).quizStatus()).isEqualTo(QuizStatus.FAILED);
    }

    private User student() {
        return User.builder().id(UUID.randomUUID()).keycloakId("kc-student").email("etu@univ.ma")
            .firstName("Sara").lastName("Etudiante").role(Role.ETUDIANT).build();
    }

    private Quiz publishedQuizWithQuestion() {
        Quiz quiz = Quiz.builder()
            .id(UUID.randomUUID()).document(document).teacher(teacher)
            .title("Quiz IA").difficulty(Difficulty.MOYEN).nbQuestions(1)
            .quizStatus(QuizStatus.PUBLISHED).questions(new ArrayList<>()).build();
        quiz.getQuestions().add(Question.builder().id(UUID.randomUUID()).quiz(quiz).type(QuestionType.QCM)
            .content("Q ?").options(List.of("A", "B", "C", "D")).correctAnswer("A")
            .explanation("Parce que A").difficulty(Difficulty.MOYEN).position(0).build());
        return quiz;
    }

    private void stubStudent(User student, Quiz quiz, boolean hasAttempt) {
        when(userRepository.findByKeycloakId("kc-student")).thenReturn(Optional.of(student));
        when(quizRepository.findByIdWithQuestions(quiz.getId())).thenReturn(Optional.of(quiz));
        when(attemptRepository.existsBySession_Quiz_IdAndStudent_Id(quiz.getId(), student.getId()))
            .thenReturn(hasAttempt);
    }

    @Test
    @DisplayName("getById : 403 pour un étudiant sans tentative sur ce quiz")
    void getById_forbidsStudentWithoutAttempt() {
        User student = student();
        Quiz quiz = publishedQuizWithQuestion();
        stubStudent(student, quiz, false);

        assertThatThrownBy(() -> quizService.getById(quiz.getId(), "kc-student"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("non autorisé");
    }

    @Test
    @DisplayName("getById : un étudiant en cours d'examen ne reçoit ni réponses ni explications")
    void getById_hidesAnswersFromStudentBeforeSubmission() {
        User student = student();
        Quiz quiz = publishedQuizWithQuestion();
        stubStudent(student, quiz, true);
        when(attemptRepository.existsBySession_Quiz_IdAndStudent_IdAndAttemptStatus(
            quiz.getId(), student.getId(), AttemptStatus.SUBMITTED)).thenReturn(false);

        QuestionDto q = quizService.getById(quiz.getId(), "kc-student").questions().get(0);

        assertThat(q.content()).isEqualTo("Q ?");
        assertThat(q.correctAnswer()).isNull();
        assertThat(q.explanation()).isNull();
    }

    @Test
    @DisplayName("getById : un étudiant ayant soumis voit la correction")
    void getById_showsAnswersToStudentAfterSubmission() {
        User student = student();
        Quiz quiz = publishedQuizWithQuestion();
        stubStudent(student, quiz, true);
        when(attemptRepository.existsBySession_Quiz_IdAndStudent_IdAndAttemptStatus(
            quiz.getId(), student.getId(), AttemptStatus.SUBMITTED)).thenReturn(true);

        assertThat(quizService.getById(quiz.getId(), "kc-student").questions().get(0).correctAnswer())
            .isEqualTo("A");
    }

    @Test
    @DisplayName("create : une génération perdue (délai dépassé) ne bloque plus le document")
    void create_unblocksDocument_whenPreviousGenerationOverdue() {
        Quiz stale = draftQuiz(LocalDateTime.now().minusHours(2));
        QuizCreateRequest req = new QuizCreateRequest(document.getId(), "Quiz IA", null, Difficulty.MOYEN, 5, null);

        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(documentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        when(quizRepository.findByDocument_IdAndQuizStatusAndNlpTaskIdIsNotNull(document.getId(), QuizStatus.DRAFT))
            .thenReturn(List.of(stale));
        when(nlpClient.pollStatus("task-abc")).thenReturn(new NlpTaskStatus("task-abc", "PENDING", 0, null, null));
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(nlpClient.submitGeneration(any())).thenReturn("task-new");

        QuizDto result = quizService.create(req, TEACHER_KC_ID);

        assertThat(stale.getQuizStatus()).isEqualTo(QuizStatus.FAILED);
        assertThat(result.nlpTaskId()).isEqualTo("task-new");
    }

    @Test
    @DisplayName("getById : ne poll pas le microservice NLP si le quiz n'est plus en DRAFT")
    void getById_skipsPolling_whenNotDraft() {
        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        Quiz quiz = Quiz.builder()
            .id(UUID.randomUUID()).document(document).teacher(teacher)
            .title("Quiz IA").difficulty(Difficulty.MOYEN).nbQuestions(1)
            .quizStatus(QuizStatus.PUBLISHED).build();

        when(quizRepository.findByIdWithQuestions(quiz.getId())).thenReturn(Optional.of(quiz));

        QuizDto result = quizService.getById(quiz.getId(), TEACHER_KC_ID);

        assertThat(result.quizStatus()).isEqualTo(QuizStatus.PUBLISHED);
        verifyNoInteractions(nlpClient);
    }

    // ── updateQuestions ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateQuestions : valide un QCM avec 4 options correctes")
    void updateQuestions_validQcm() {
        Quiz quiz = Quiz.builder()
            .id(UUID.randomUUID()).document(document).teacher(teacher)
            .title("Quiz IA").difficulty(Difficulty.MOYEN).nbQuestions(1)
            .quizStatus(QuizStatus.REVIEWING).build();

        QuestionUpdateRequest qr = new QuestionUpdateRequest(
            null, QuestionType.QCM,
            "Qu'est-ce que le machine learning ?",
            List.of("Apprentissage automatique", "Base de données", "Langage", "OS"),
            "Apprentissage automatique",
            "Explication", Difficulty.MOYEN, List.of("ML"), 0
        );
        QuizUpdateQuestionsRequest updateReq = new QuizUpdateQuestionsRequest(List.of(qr), true);

        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(quizRepository.findByIdWithQuestions(quiz.getId())).thenReturn(Optional.of(quiz));
        when(quizRepository.save(any())).thenReturn(quiz);

        QuizDto result = quizService.updateQuestions(quiz.getId(), updateReq, TEACHER_KC_ID);

        assertThat(result).isNotNull();
        verify(questionRepository).deleteByQuiz_Id(quiz.getId());
    }

    @Test
    @DisplayName("updateQuestions : rejette un QCM avec 3 options")
    void updateQuestions_invalidQcm() {
        Quiz quiz = Quiz.builder()
            .id(UUID.randomUUID()).document(document).teacher(teacher)
            .title("Quiz").difficulty(Difficulty.MOYEN).nbQuestions(1)
            .quizStatus(QuizStatus.REVIEWING).build();

        QuestionUpdateRequest qr = new QuestionUpdateRequest(
            null, QuestionType.QCM, "Question ?",
            List.of("A", "B", "C"),  // Seulement 3 options — invalide
            "A", null, Difficulty.MOYEN, null, 0
        );

        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(quizRepository.findByIdWithQuestions(quiz.getId())).thenReturn(Optional.of(quiz));

        assertThatThrownBy(() -> quizService.updateQuestions(
                quiz.getId(), new QuizUpdateQuestionsRequest(List.of(qr), false), TEACHER_KC_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("4 options");
    }

    // ── listForTeacher ────────────────────────────────────────────────────────

    @Test
    @DisplayName("listForTeacher : retourne une page paginée")
    void listForTeacher_paged() {
        Quiz quiz = Quiz.builder()
            .id(UUID.randomUUID()).document(document).teacher(teacher)
            .title("Quiz").difficulty(Difficulty.MOYEN).nbQuestions(5)
            .quizStatus(QuizStatus.PUBLISHED).build();

        Page<Quiz> page = new PageImpl<>(List.of(quiz));

        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(quizRepository.findByTeacher_IdOrderByCreatedAtDesc(teacher.getId(), PageRequest.of(0, 10)))
            .thenReturn(page);

        PageResponse<QuizDto> result = quizService.listForTeacher(TEACHER_KC_ID, PageRequest.of(0, 10));

        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    // ── quiz inachevé : liste, relance, édition, suppression ──────────────────

    @Test
    @DisplayName("listForTeacher : rafraîchit les générations en cours (plus de « Brouillon » figé)")
    void listForTeacher_advancesStaleDrafts() {
        Quiz stale = draftQuiz(LocalDateTime.now().minusHours(2));
        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(quizRepository.findByTeacher_IdOrderByCreatedAtDesc(eq(teacher.getId()), any()))
            .thenReturn(new PageImpl<>(List.of(stale)));
        when(nlpClient.pollStatus("task-abc")).thenReturn(new NlpTaskStatus("task-abc", "PENDING", 0, null, null));
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PageResponse<QuizDto> result = quizService.listForTeacher(TEACHER_KC_ID, PageRequest.of(0, 10));

        assertThat(result.content().get(0).quizStatus()).isEqualTo(QuizStatus.FAILED);
    }

    @Test
    @DisplayName("regenerate : relance un quiz FAILED avec ses types d'origine")
    void regenerate_resubmitsFailedQuiz() {
        Quiz failed = draftQuiz(LocalDateTime.now().minusHours(2));
        failed.setQuizStatus(QuizStatus.FAILED);
        failed.setQuestionTypes("QCM,EXERCICE");
        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(quizRepository.findByIdWithQuestions(failed.getId())).thenReturn(Optional.of(failed));
        when(nlpClient.submitGeneration(any())).thenReturn("task-retry");
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuizDto result = quizService.regenerate(failed.getId(), TEACHER_KC_ID);

        assertThat(result.quizStatus()).isEqualTo(QuizStatus.DRAFT);
        assertThat(result.nlpTaskId()).isEqualTo("task-retry");
        assertThat(failed.getGenerationStartedAt()).isAfter(LocalDateTime.now().minusMinutes(1));
        verify(nlpClient).submitGeneration(argThat(r ->
            r.questionTypes().equals(List.of(QuestionType.QCM, QuestionType.EXERCICE))));
    }

    @Test
    @DisplayName("regenerate : 409 si le quiz n'est pas en échec")
    void regenerate_rejectsNonFailedQuiz() {
        Quiz reviewing = draftQuiz(LocalDateTime.now());
        reviewing.setQuizStatus(QuizStatus.REVIEWING);
        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(quizRepository.findByIdWithQuestions(reviewing.getId())).thenReturn(Optional.of(reviewing));

        assertThatThrownBy(() -> quizService.regenerate(reviewing.getId(), TEACHER_KC_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("échoué");
        verifyNoInteractions(nlpClient);
    }

    @Test
    @DisplayName("updateQuestions : 409 pendant une génération en cours")
    void updateQuestions_blockedWhileGenerating() {
        Quiz generating = draftQuiz(LocalDateTime.now());
        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(quizRepository.findByIdWithQuestions(generating.getId())).thenReturn(Optional.of(generating));

        QuizUpdateQuestionsRequest req = new QuizUpdateQuestionsRequest(List.of(), false);

        assertThatThrownBy(() -> quizService.updateQuestions(generating.getId(), req, TEACHER_KC_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Génération en cours");
    }

    @Test
    @DisplayName("updateQuestions : un quiz FAILED peut être complété à la main")
    void updateQuestions_allowsManualAuthoringOfFailedQuiz() {
        Quiz failed = draftQuiz(LocalDateTime.now());
        failed.setQuizStatus(QuizStatus.FAILED);
        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(quizRepository.findByIdWithQuestions(failed.getId())).thenReturn(Optional.of(failed));
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuestionUpdateRequest q = new QuestionUpdateRequest(
            null, QuestionType.OUVERTE, "Définir un SI.", null, "Un système d'information.",
            null, Difficulty.MOYEN, null, 0);

        QuizDto result = quizService.updateQuestions(failed.getId(), new QuizUpdateQuestionsRequest(List.of(q), false), TEACHER_KC_ID);

        assertThat(result.quizStatus()).isEqualTo(QuizStatus.REVIEWING);
        assertThat(result.questions()).hasSize(1);
    }

    @Test
    @DisplayName("delete : supprime un quiz en cours de génération")
    void delete_allowsGeneratingQuiz() {
        Quiz generating = draftQuiz(LocalDateTime.now());
        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(quizRepository.findByIdWithQuestions(generating.getId())).thenReturn(Optional.of(generating));
        when(sessionRepository.existsByQuiz_Id(generating.getId())).thenReturn(false);

        quizService.delete(generating.getId(), TEACHER_KC_ID);

        verify(nlpClient).cancelGeneration("task-abc");
        verify(quizRepository).delete(generating);
    }

    @Test
    @DisplayName("delete : 409 si le quiz est utilisé par des sessions (résultats étudiants)")
    void delete_refusedWhenUsedBySessions() {
        Quiz published = draftQuiz(LocalDateTime.now());
        published.setQuizStatus(QuizStatus.PUBLISHED);
        when(userRepository.findByKeycloakId(TEACHER_KC_ID)).thenReturn(Optional.of(teacher));
        when(quizRepository.findByIdWithQuestions(published.getId())).thenReturn(Optional.of(published));
        when(sessionRepository.existsByQuiz_Id(published.getId())).thenReturn(true);

        assertThatThrownBy(() -> quizService.delete(published.getId(), TEACHER_KC_ID))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("sessions");
        verify(quizRepository, never()).delete(any());
    }
}
