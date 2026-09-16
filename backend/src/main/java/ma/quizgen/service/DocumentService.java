package ma.quizgen.service;

import ma.quizgen.dto.DocumentDto;
import ma.quizgen.dto.DocumentUploadResponse;
import ma.quizgen.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface DocumentService {

    /**
     * Upload un PDF vers MinIO et persiste les métadonnées en BDD.
     *
     * @param file fichier PDF (max 50 Mo, type MIME application/pdf)
     * @return réponse avec l'ID du document et l'objectKey MinIO
     */
    DocumentUploadResponse uploadDocument(MultipartFile file);

    /**
     * Liste les documents appartenant à l'enseignant connecté (paginé).
     */
    PageResponse<DocumentDto> listMyDocuments(Pageable pageable);

    /**
     * Retourne les métadonnées d'un document (accessible par son propriétaire).
     */
    DocumentDto getDocumentById(UUID id);

    /**
     * Génère une URL présignée pour télécharger le PDF (valable 1 heure).
     */
    String getDownloadUrl(UUID id);

    /**
     * Supprime le document de MinIO et de la BDD.
     * Seul le propriétaire ou un ADMIN peut supprimer.
     */
    void deleteDocument(UUID id);
}
