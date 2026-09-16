package ma.quizgen.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.dto.DocumentDto;
import ma.quizgen.dto.DocumentUploadResponse;
import ma.quizgen.dto.PageResponse;
import ma.quizgen.entity.Document;
import ma.quizgen.entity.User;
import ma.quizgen.repository.DocumentRepository;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.security.CurrentUser;
import ma.quizgen.service.DocumentService;
import ma.quizgen.service.MinioService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final long   MAX_FILE_SIZE = 50L * 1024 * 1024; // 50 Mo
    private static final String PDF_MIME      = MediaType.APPLICATION_PDF_VALUE;

    private final DocumentRepository documentRepository;
    private final UserRepository     userRepository;
    private final MinioService       minioService;
    private final CurrentUser        currentUser;

    @Value("${minio.bucket-documents}")
    private String bucketDocuments;

    // ── Upload ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DocumentUploadResponse uploadDocument(MultipartFile file) {
        validateFile(file);

        User owner = resolveCurrentUser();
        String objectKey = buildObjectKey(owner.getId(), file.getOriginalFilename());

        String etag;
        try {
            etag = minioService.uploadFile(
                bucketDocuments,
                objectKey,
                file.getInputStream(),
                PDF_MIME,
                file.getSize()
            );
        } catch (Exception e) {
            throw new RuntimeException("Échec de l'upload: " + e.getMessage(), e);
        }

        Document doc = Document.builder()
            .user(owner)
            .originalFilename(file.getOriginalFilename())
            .bucketName(bucketDocuments)
            .objectKey(objectKey)
            .etag(etag)
            .fileSize(file.getSize())
            .build();

        Document saved = documentRepository.save(doc);
        log.info("Document enregistré: {} (id={})", saved.getOriginalFilename(), saved.getId());

        return new DocumentUploadResponse(
            saved.getId(),
            saved.getOriginalFilename(),
            saved.getObjectKey(),
            saved.getFileSize(),
            "Document uploadé avec succès"
        );
    }

    // ── List ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocumentDto> listMyDocuments(Pageable pageable) {
        User owner = resolveCurrentUser();
        Page<DocumentDto> page = documentRepository.findAllByUser(owner, pageable)
            .map(this::toDto);
        return PageResponse.of(page);
    }

    // ── Get ───────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public DocumentDto getDocumentById(UUID id) {
        return toDto(resolveDocument(id));
    }

    // ── Download URL ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public String getDownloadUrl(UUID id) {
        Document doc = resolveDocument(id);
        return minioService.getPresignedUrl(doc.getBucketName(), doc.getObjectKey(), 3600);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteDocument(UUID id) {
        Document doc = resolveDocument(id);
        minioService.deleteObject(doc.getBucketName(), doc.getObjectKey());
        documentRepository.delete(doc);
        log.info("Document supprimé: {} (id={})", doc.getOriginalFilename(), id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                "Fichier trop volumineux (max 50 Mo). Taille reçue: "
                + file.getSize() / (1024 * 1024) + " Mo");
        }
        String contentType = file.getContentType();
        if (!PDF_MIME.equalsIgnoreCase(contentType)) {
            throw new IllegalArgumentException(
                "Seuls les fichiers PDF sont acceptés. Type reçu: " + contentType);
        }
    }

    private String buildObjectKey(UUID userId, String filename) {
        // Ex: documents/550e8400-e29b-41d4-a716/cours-algo-2025.pdf
        String safeName = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return "documents/" + userId + "/" + UUID.randomUUID() + "_" + safeName;
    }

    private User resolveCurrentUser() {
        String keycloakId = currentUser.getKeycloakId();
        return userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new RuntimeException(
                "Utilisateur non synchronisé. Appelez POST /users/sync d'abord."));
    }

    private Document resolveDocument(UUID id) {
        User owner = resolveCurrentUser();
        // ADMIN peut accéder à tout, ENSEIGNANT uniquement ses propres documents
        if (currentUser.hasRole("ADMIN")) {
            return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document introuvable: " + id));
        }
        return documentRepository.findByIdAndUser(id, owner)
            .orElseThrow(() -> new RuntimeException(
                "Document introuvable ou accès refusé: " + id));
    }

    private DocumentDto toDto(Document d) {
        return new DocumentDto(
            d.getId(),
            d.getOriginalFilename(),
            d.getBucketName(),
            d.getObjectKey(),
            d.getFileSize(),
            d.getPageCount(),
            d.getIsProcessed(),
            d.getCreatedAt()
        );
    }
}
