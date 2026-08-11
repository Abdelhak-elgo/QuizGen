package ma.quizgen.repository;

import ma.quizgen.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByQuiz_IdOrderByPositionAsc(UUID quizId);

    void deleteByQuiz_Id(UUID quizId);
}
