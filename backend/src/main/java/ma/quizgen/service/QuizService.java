package ma.quizgen.service;

import ma.quizgen.dto.PageResponse;
import ma.quizgen.dto.QuizCreateRequest;
import ma.quizgen.dto.QuizDto;
import ma.quizgen.dto.QuizUpdateQuestionsRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface QuizService {

    /** Crée un quiz et déclenche la génération NLP (bloquant avec polling). */
    QuizDto create(QuizCreateRequest request, String teacherKeycloakId);

    QuizDto getById(UUID quizId, String requesterKeycloakId);

    PageResponse<QuizDto> listForTeacher(String teacherKeycloakId, Pageable pageable);

    /** Édite / valide les questions générées et publie si demandé. */
    QuizDto updateQuestions(UUID quizId, QuizUpdateQuestionsRequest request, String teacherKeycloakId);

    void delete(UUID quizId, String teacherKeycloakId);
}
