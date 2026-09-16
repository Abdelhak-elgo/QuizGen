package ma.quizgen.repository;

import ma.quizgen.entity.Document;
import ma.quizgen.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findAllByUser(User user, Pageable pageable);

    Optional<Document> findByIdAndUser(UUID id, User user);

    long countByUser(User user);
}
