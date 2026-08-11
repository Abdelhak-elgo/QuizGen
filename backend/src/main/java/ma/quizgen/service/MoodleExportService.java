package ma.quizgen.service;

import java.util.UUID;

/**
 * Génère un fichier XML au format Moodle Quiz (importable dans Moodle 4.x).
 *
 * Format produit : Moodle XML Question Format
 * - QCM → question type="multichoice"
 * - OUVERTE → question type="essay"
 * - EXERCICE → question type="shortanswer"
 */
public interface MoodleExportService {

    /**
     * @param quizId ID du quiz (doit être PUBLISHED)
     * @param teacherKeycloakId ID Keycloak de l'enseignant (vérification ownership)
     * @return contenu XML UTF-8 importable dans Moodle
     */
    byte[] exportToMoodleXml(UUID quizId, String teacherKeycloakId);
}
