package ma.quizgen.service;

import ma.quizgen.dto.BertScoreDetailDto;
import ma.quizgen.entity.Question;

import java.util.List;
import java.util.Map;

/**
 * Client pour le microservice NLP — endpoints BERTScore et Evidence RAG.
 * Calcule la similarité sémantique entre les réponses ouvertes des étudiants
 * et les réponses de référence, puis enrichit chaque résultat avec le passage
 * source le plus pertinent (Evidence RAG) pour une correction explicable.
 */
public interface BertScoreClient {

    /**
     * Corrige en batch les réponses ouvertes d'une tentative.
     *
     * <p>Après le scoring BERTScore, tente de récupérer un "passage-evidence"
     * depuis ChromaDB pour chaque question ouverte. Si le service NLP ou
     * ChromaDB est indisponible, le champ {@code evidenceChunk} des DTOs
     * retournés est simplement {@code null} — la correction continue normalement.</p>
     *
     * @param openQuestions  Liste des questions ouvertes/exercices du quiz
     * @param studentAnswers Map question_id → réponse_étudiant
     * @param documentId     UUID du document source (pour la récupération RAG evidence).
     *                       Peut être {@code null} si non disponible ; dans ce cas,
     *                       aucune evidence ne sera tentée.
     * @return Liste de BertScoreDetailDto (une entrée par question ouverte répondue)
     */
    List<BertScoreDetailDto> scoreOpenAnswers(
        List<Question> openQuestions,
        Map<String, String> studentAnswers,
        String documentId
    );
}
