package ma.quizgen.service;

import java.util.UUID;

/**
 * Génère une archive ZIP SCORM 2004 pour un quiz publié.
 *
 * L'archive contient :
 * - imsmanifest.xml (manifest SCORM 2004, 3rd Edition)
 * - index.html (point d'entrée)
 * - content/ : une page HTML par question avec JS SCORM API
 * - lib/scorm-wrapper.js : wrapper léger pour l'API SCORM 2004 RTE
 */
public interface ScormExportService {

    /**
     * @param quizId ID du quiz (doit être PUBLISHED)
     * @param teacherKeycloakId ID Keycloak de l'enseignant (vérification ownership)
     * @return contenu binaire du ZIP SCORM
     */
    byte[] exportToScorm(UUID quizId, String teacherKeycloakId);
}
