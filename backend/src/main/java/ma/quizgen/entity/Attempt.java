package ma.quizgen.entity;

import jakarta.persistence.*;
import lombok.*;
import ma.quizgen.entity.enums.AttemptStatus;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
    name = "attempts",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_attempt_session_student",
        columnNames = {"session_id", "student_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    /**
     * Réponses de l'étudiant : Map&lt;question_id (String), réponse (String)&gt;.
     * Stocké en JSONB en base.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> answers;

    /** Score obtenu (QCM + contribution partielle des réponses ouvertes via BERTScore). */
    @Builder.Default
    private Integer score = 0;

    /** Score maximum possible (nombre de questions). */
    @Column(name = "max_score")
    private Integer maxScore;

    /**
     * Détail des scores BERTScore par question ouverte/exercice.
     * Map&lt;question_id (String), BertScoreDetail (JSON)&gt;.
     * Null si aucune question ouverte.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bert_score_details", columnDefinition = "jsonb")
    private Map<String, Object> bertScoreDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_status", nullable = false, length = 20)
    @Builder.Default
    private AttemptStatus attemptStatus = AttemptStatus.IN_PROGRESS;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
