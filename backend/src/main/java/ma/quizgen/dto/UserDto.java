package ma.quizgen.dto;

import ma.quizgen.entity.enums.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserDto(
    UUID id,
    String keycloakId,
    String email,
    String firstName,
    String lastName,
    Role role,
    Boolean isActive,
    LocalDateTime createdAt
) {}
