package ma.quizgen.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentDto(
    UUID id,
    String originalFilename,
    String bucketName,
    String objectKey,
    Long fileSize,
    Integer pageCount,
    Boolean isProcessed,
    LocalDateTime createdAt
) {}
