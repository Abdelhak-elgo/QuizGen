package ma.quizgen.service;

import ma.quizgen.dto.BertScoreDetailDto;
import ma.quizgen.entity.Question;

import java.util.List;
import java.util.Map;

/**
 * Client pour le microservice NLP — endpoint BERTScore.
 * Calcule la similarité sémantique entre les réponses ouvertes des étudiants
 * et les réponses de référence.
 */
public interface BertScoreClient {

    /**
     * Corrige en batch les réponses ouvertes d'une tentative.
     *
     * @param openQuestions  Liste des questions ouvertes/exercices du quiz
     * @param studentAnswers Map question_id → réponse_étudiant
     * @return Liste de BertScoreDetailDto (une entrée par question ouverte répondue)
     */
    List<BertScoreDetailDto> scoreOpenAnswers(
        List<Question> openQuestions,
        Map<String, String> studentAnswers
    );
}
