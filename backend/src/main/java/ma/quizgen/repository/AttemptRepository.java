package ma.quizgen.repository;

import ma.quizgen.entity.Attempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttemptRepository extends JpaRepository<Attempt, UUID> {

    Optional<Attempt> findBySession_IdAndStudent_Id(UUID sessionId, UUID studentId);

    boolean existsBySession_IdAndStudent_Id(UUID sessionId, UUID studentId);

    List<Attempt> findBySession_Id(UUID sessionId);

    @Query("""
        SELECT a FROM Attempt a
        WHERE a.session.quiz.id = :quizId
        AND a.attemptStatus = 'SUBMITTED'
        """)
    List<Attempt> findSubmittedByQuizId(@Param("quizId") UUID quizId);

    Page<Attempt> findByStudent_IdOrderByStartedAtDesc(UUID studentId, Pageable pageable);

    @Query("""
        SELECT AVG(a.score * 100.0 / NULLIF(a.maxScore, 0))
        FROM Attempt a
        WHERE a.session.quiz.id = :quizId
        AND a.attemptStatus = 'SUBMITTED'
        """)
    Double findAverageScoreByQuizId(@Param("quizId") UUID quizId);

    @Query("""
        SELECT COUNT(a)
        FROM Attempt a
        WHERE a.session.quiz.id = :quizId
        AND a.attemptStatus = 'SUBMITTED'
        AND a.score * 100.0 / NULLIF(a.maxScore, 0) >= :threshold
        """)
    Long countPassingAttemptsByQuizId(@Param("quizId") UUID quizId, @Param("threshold") double threshold);

    @Query("""
        SELECT COUNT(a)
        FROM Attempt a
        WHERE a.session.quiz.id = :quizId
        AND a.attemptStatus = 'SUBMITTED'
        """)
    Long countSubmittedAttemptsByQuizId(@Param("quizId") UUID quizId);
}
