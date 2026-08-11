package ma.quizgen.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ma.quizgen.entity.enums.Difficulty;
import ma.quizgen.entity.enums.QuestionType;

import java.util.List;

/** Requête envoyée au microservice FastAPI NLP. */
public record NlpGenerateRequest(
    @JsonProperty("document_id")  String documentId,
    @JsonProperty("bucket_name")  String bucketName,
    @JsonProperty("object_key")   String objectKey,
    @JsonProperty("nb_questions") int nbQuestions,
    @JsonProperty("question_types") List<QuestionType> questionTypes,
    Difficulty difficulty
) {}
