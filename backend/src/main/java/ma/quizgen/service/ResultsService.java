package ma.quizgen.service;

import ma.quizgen.dto.AttemptReviewDto;
import ma.quizgen.dto.RecommendationDto;
import ma.quizgen.dto.SessionResultsDto;

import java.util.UUID;

/** Suivi des résultats par l'enseignant : sessions, copies, recommandations. */
public interface ResultsService {

    /** Tous les étudiants de la session (assignés ou ayant rejoint), avec statut et score. */
    SessionResultsDto sessionResults(UUID sessionId, String teacherKeycloakId);

    /** Copie d'un étudiant corrigée question par question. */
    AttemptReviewDto attemptReview(UUID attemptId, String teacherKeycloakId);

    /** Génère (ou régénère) le diagnostic pédagogique d'une copie soumise. */
    RecommendationDto generateRecommendation(UUID attemptId, String teacherKeycloakId);
}
