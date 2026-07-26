package ma.quizgen.repository;

import ma.quizgen.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByUploadedByIdOrderByUploadedAtDesc(Long userId);

    Optional<Document> findByObjectKey(String objectKey);

    boolean existsByObjectKey(String objectKey);
}
