package ma.quizgen.repository;

import ma.quizgen.entity.Session;
import ma.quizgen.entity.enums.SessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    Page<Session> findByTeacher_IdOrderByStartTimeDesc(UUID teacherId, Pageable pageable);

    Optional<Session> findByAccessCodeAndSessionStatus(String accessCode, SessionStatus status);

    @Query("SELECT s FROM Session s WHERE s.sessionStatus = 'SCHEDULED' AND s.startTime <= :now")
    List<Session> findSessionsToOpen(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM Session s WHERE s.sessionStatus = 'OPEN' AND s.endTime <= :now")
    List<Session> findSessionsToClose(@Param("now") LocalDateTime now);

    Page<Session> findByQuiz_IdOrderByStartTimeDesc(UUID quizId, Pageable pageable);
}
