package ma.quizgen.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.dto.*;
import ma.quizgen.entity.Document;
import ma.quizgen.entity.Question;
import ma.quizgen.entity.Quiz;
import ma.quizgen.entity.User;
import ma.quizgen.entity.enums.Difficulty;
import ma.quizgen.entity.enums.QuestionType;
import ma.quizgen.entity.enums.QuizStatus;
import ma.quizgen.repository.DocumentRepository;
import ma.quizgen.repository.QuestionRepository;
import ma.quizgen.repository.QuizRepository;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.service.NlpServiceClient;
import ma.quizgen.service.QuizService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
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
            .quizStatus(QuizStatus.DRAFT)
            .build());

        NlpGenerateRequest nlpReq = new NlpGenerateRequest(
            doc.getId().toString(),
            doc.getBucketName(),
            doc.getObjectKey(),
            request.nbQuestions(),
            types,
            request.difficulty()
        );

        try {
            NlpTaskStatus result = nlpClient.submitAndWait(nlpReq);
            quiz.setNlpTaskId(result.taskId());
            quiz.getQuestions().addAll(mapNlpToQuestions(result.result(), quiz));
            quiz.setQuizStatus(QuizStatus.REVIEWING);
            quiz = quizRepository.save(quiz);
            log.info("Quiz {} généré ({} questions)", quiz.getId(), quiz.getQuestions().size());
        } catch (Exception e) {
            log.error("Échec NLP pour quiz {}: {}", quiz.getId(), e.getMessage());
            quiz.setQuizStatus(QuizStatus.DRAFT);
            quizRepository.save(quiz);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Génération NLP échouée : " + e.getMessage());
        }

        return QuizDto.from(quiz);
    }

    @Override
    @Transactional(readOnly = true)
    public QuizDto getById(UUID quizId, String requesterKeycloakId) {
        Quiz quiz = quizRepository.findByIdWithQuestions(quizId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz introuvable"));
        return QuizDto.from(quiz);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuizDto> listForTeacher(String teacherKeycloakId, Pageable pageable) {
        User teacher = resolveUser(teacherKeycloakId);
        Page<Quiz> page = quizRepository.findByTeacher_IdOrderByCreatedAtDesc(teacher.getId(), pageable);
        return PageResponse.of(page.map(QuizDto::from));
    }

    @Override
    @Transactional
    public QuizDto updateQuestions(UUID quizId, QuizUpdateQuestionsRequest request, String teacherKeycloakId) {
        User teacher = resolveUser(teacherKeycloakId);
        Quiz quiz = quizRepository.findByIdWithQuestions(quizId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz introuvable"));

        if (!quiz.getTeacher().getId().equals(teacher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non autorisé");
        }
        if (quiz.getQuizStatus() == QuizStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un quiz archivé ne peut pas être modifié");
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
        User teacher = resolveUser(teacherKeycloakId);
        Quiz quiz = quizRepository.findById(quizId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quiz introuvable"));
        if (!quiz.getTeacher().getId().equals(teacher.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non autorisé");
        }
        quizRepository.delete(quiz);
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
