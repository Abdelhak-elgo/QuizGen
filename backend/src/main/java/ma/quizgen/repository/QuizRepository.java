package ma.quizgen.repository;

import ma.quizgen.entity.Quiz;
import ma.quizgen.entity.enums.QuizStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    Page<Quiz> findByTeacher_IdOrderByCreatedAtDesc(UUID teacherId, Pageable pageable);

    Page<Quiz> findByQuizStatusOrderByCreatedAtDesc(QuizStatus status, Pageable pageable);

    // LEFT JOIN : un quiz en cours de génération (DRAFT) n'a encore aucune question
    @Query("SELECT q FROM Quiz q LEFT JOIN FETCH q.questions WHERE q.id = :id")
    Optional<Quiz> findByIdWithQuestions(@Param("id") UUID id);

    boolean existsByDocument_IdAndTeacher_Id(UUID documentId, UUID teacherId);

    boolean existsByDocument_Id(UUID documentId);

    boolean existsByDocument_IdAndQuizStatusAndNlpTaskIdIsNotNull(UUID documentId, QuizStatus quizStatus);

    List<Quiz> findByDocument_IdAndQuizStatusAndNlpTaskIdIsNotNull(UUID documentId, QuizStatus quizStatus);
}
