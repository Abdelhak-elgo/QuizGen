package ma.quizgen.service;

import ma.quizgen.dto.DocumentUploadResponse;
import ma.quizgen.entity.Document;
import ma.quizgen.entity.User;
import ma.quizgen.entity.enums.Role;
import ma.quizgen.repository.DocumentRepository;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.security.CurrentUser;
import ma.quizgen.service.impl.DocumentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentServiceImpl — tests unitaires")
class DocumentServiceImplTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private UserRepository     userRepository;
    @Mock private MinioService       minioService;
    @Mock private CurrentUser        currentUser;

    @InjectMocks private DocumentServiceImpl documentService;

    private static final String KC_ID  = "kc-teacher-001";
    private static final UUID   USER_ID = UUID.randomUUID();
    private User teacher;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(documentService, "bucketDocuments", "documents");

        teacher = User.builder()
            .id(USER_ID)
            .keycloakId(KC_ID)
            .email("prof@univ.ma")
            .role(Role.ENSEIGNANT)
            .build();

        when(currentUser.getKeycloakId()).thenReturn(KC_ID);
        when(userRepository.findByKeycloakId(KC_ID)).thenReturn(Optional.of(teacher));
    }

    // ── uploadDocument ────────────────────────────────────────────────────────

    @Test
    @DisplayName("uploadDocument — cas nominal : PDF valide stocké dans MinIO et BDD")
    void uploadDocument_shouldSucceed_withValidPdf() {
        byte[] content = new byte[1024];
        MockMultipartFile file = new MockMultipartFile(
            "file", "cours.pdf", "application/pdf", content);

        when(minioService.uploadFile(anyString(), anyString(), any(), anyString(), anyLong()))
            .thenReturn("etag-abc123");
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> {
            Document d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        DocumentUploadResponse response = documentService.uploadDocument(file);

        assertThat(response.originalFilename()).isEqualTo("cours.pdf");
        assertThat(response.message()).contains("succès");
        verify(minioService).uploadFile(eq("documents"), anyString(), any(), anyString(), anyLong());
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("uploadDocument — rejette un fichier non-PDF")
    void uploadDocument_shouldReject_nonPdfFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "cours.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            new byte[100]);

        assertThatThrownBy(() -> documentService.uploadDocument(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PDF");

        verifyNoInteractions(minioService);
        verifyNoInteractions(documentRepository);
    }

    @Test
    @DisplayName("uploadDocument — rejette un fichier vide")
    void uploadDocument_shouldReject_emptyFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> documentService.uploadDocument(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vide");
    }

    @Test
    @DisplayName("uploadDocument — rejette un fichier > 50 Mo")
    void uploadDocument_shouldReject_oversizedFile() {
        // 51 Mo
        byte[] bigContent = new byte[51 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
            "file", "big.pdf", "application/pdf", bigContent);

        assertThatThrownBy(() -> documentService.uploadDocument(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("50 Mo");
    }

    // ── deleteDocument ────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteDocument — supprime de MinIO et de la BDD")
    void deleteDocument_shouldDeleteFromMinioAndDb() {
        UUID docId = UUID.randomUUID();
        Document doc = Document.builder()
            .id(docId)
            .user(teacher)
            .originalFilename("cours.pdf")
            .bucketName("documents")
            .objectKey("documents/" + USER_ID + "/cours.pdf")
            .build();

        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(documentRepository.findByIdAndUser(docId, teacher)).thenReturn(Optional.of(doc));

        documentService.deleteDocument(docId);

        verify(minioService).deleteObject("documents", doc.getObjectKey());
        verify(documentRepository).delete(doc);
    }

    @Test
    @DisplayName("deleteDocument — lève une exception si document introuvable")
    void deleteDocument_shouldThrow_whenNotFound() {
        UUID docId = UUID.randomUUID();
        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(documentRepository.findByIdAndUser(docId, teacher)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.deleteDocument(docId))
            .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(minioService);
    }

    // ── getDocumentById ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getDocumentById — retourne le DTO si le document appartient à l'utilisateur")
    void getDocumentById_shouldReturnDto_whenOwned() {
        UUID docId = UUID.randomUUID();
        Document doc = Document.builder()
            .id(docId)
            .user(teacher)
            .originalFilename("algo.pdf")
            .bucketName("documents")
            .objectKey("documents/" + USER_ID + "/algo.pdf")
            .fileSize(1024L)
            .isProcessed(false)
            .build();

        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(documentRepository.findByIdAndUser(docId, teacher)).thenReturn(Optional.of(doc));

        var result = documentService.getDocumentById(docId);

        assertThat(result.originalFilename()).isEqualTo("algo.pdf");
        assertThat(result.id()).isEqualTo(docId);
    }

    // ── getDownloadUrl ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getDownloadUrl — délègue à MinioService et retourne l'URL")
    void getDownloadUrl_shouldReturnPresignedUrl() {
        UUID docId = UUID.randomUUID();
        Document doc = Document.builder()
            .id(docId)
            .user(teacher)
            .bucketName("documents")
            .objectKey("documents/key.pdf")
            .build();

        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(documentRepository.findByIdAndUser(docId, teacher)).thenReturn(Optional.of(doc));
        when(minioService.getPresignedUrl("documents", "documents/key.pdf", 3600))
            .thenReturn("https://minio/presigned/url");

        String url = documentService.getDownloadUrl(docId);

        assertThat(url).isEqualTo("https://minio/presigned/url");
    }
}
