package ma.quizgen.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import ma.quizgen.dto.DocumentDto;
import ma.quizgen.dto.DocumentUploadResponse;
import ma.quizgen.dto.PageResponse;
import ma.quizgen.service.DocumentService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/documents")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Upload et gestion des documents PDF")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Upload d'un fichier PDF (max 50 Mo).
     * Réservé aux ENSEIGNANTS et ADMINS.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'ADMIN')")
    @Operation(summary = "Uploader un PDF vers MinIO")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
        @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(documentService.uploadDocument(file));
    }

    /**
     * Liste les documents de l'enseignant connecté (paginé).
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'ADMIN')")
    @Operation(summary = "Lister mes documents PDF")
    public ResponseEntity<PageResponse<DocumentDto>> listDocuments(
        @RequestParam(defaultValue = "0")  int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
            documentService.listMyDocuments(
                PageRequest.of(page, size, Sort.by("createdAt").descending())
            )
        );
    }

    /**
     * Métadonnées d'un document spécifique.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'ADMIN')")
    @Operation(summary = "Détail d'un document")
    public ResponseEntity<DocumentDto> getDocument(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getDocumentById(id));
    }

    /**
     * URL présignée pour télécharger le PDF (valable 1 heure).
     */
    @GetMapping("/{id}/download-url")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'ADMIN')")
    @Operation(summary = "Obtenir l'URL de téléchargement du PDF (1h)")
    public ResponseEntity<Map<String, String>> getDownloadUrl(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("url", documentService.getDownloadUrl(id)));
    }

    /**
     * Supprime un document (propriétaire ou ADMIN).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'ADMIN')")
    @Operation(summary = "Supprimer un document")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
