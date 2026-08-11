package ma.quizgen.dto;

import java.util.UUID;

public record DocumentUploadResponse(
    UUID id,
    String originalFilename,
    String objectKey,
    Long fileSize,
    String message
) {}
