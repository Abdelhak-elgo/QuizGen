package ma.quizgen.service;

import ma.quizgen.dto.AttemptDto;
import ma.quizgen.dto.PageResponse;
import ma.quizgen.dto.SubmitAnswersRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface AttemptService {

    /** Soumet les réponses et calcule le score QCM. */
    AttemptDto submit(UUID attemptId, SubmitAnswersRequest request, String studentKeycloakId);

    AttemptDto getById(UUID attemptId, String requesterKeycloakId);

    PageResponse<AttemptDto> listForStudent(String studentKeycloakId, Pageable pageable);
}
