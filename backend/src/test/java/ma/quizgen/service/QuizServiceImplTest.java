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
    @DisplayName("getById : libère le document (nlpTaskId=null) quand la tâche NLP échoue")
    void getById_clearsTaskId_onNlpFailure() {
        Quiz quiz = Quiz.builder()
            .id(UUID.randomUUID()).document(document).teacher(teacher)
            .title("Quiz IA").difficulty(Difficulty.MOYEN).nbQuestions(1)
            .quizStatus(QuizStatus.DRAFT).nlpTaskId("task-abc").build();

        NlpTaskStatus failure = new NlpTaskStatus("task-abc", "FAILURE", null, null, "Timeout");

        when(quizRepository.findByIdWithQuestions(quiz.getId())).thenReturn(Optional.of(quiz));
        when(nlpClient.pollStatus("task-abc")).thenReturn(failure);
        when(quizRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuizDto result = quizService.getById(quiz.getId(), TEACHER_KC_ID);

        assertThat(result.quizStatus()).isEqualTo(QuizStatus.DRAFT);
        assertThat(result.nlpTaskId()).isNull();
    }

    @Test
    @DisplayName("getById : ne poll pas le microservice NLP si le quiz n'est plus en DRAFT")
    void getById_skipsPolling_whenNotDraft() {
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
}
