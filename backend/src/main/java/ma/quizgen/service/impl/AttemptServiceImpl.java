package ma.quizgen.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.dto.AttemptDto;
import ma.quizgen.dto.BertScoreDetailDto;
import ma.quizgen.dto.PageResponse;
import ma.quizgen.dto.SubmitAnswersRequest;
import ma.quizgen.entity.Attempt;
import ma.quizgen.entity.Question;
import ma.quizgen.entity.User;
import ma.quizgen.entity.enums.AttemptStatus;
import ma.quizgen.entity.enums.QuestionType;
import ma.quizgen.repository.AttemptRepository;
import ma.quizgen.repository.QuestionRepository;
import ma.quizgen.repository.UserRepository;
import ma.quizgen.service.AttemptService;
import ma.quizgen.service.BertScoreClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttemptServiceImpl implements AttemptService {

    private final AttemptRepository  attemptRepository;
    private final QuestionRepository questionRepository;
    private final UserRepository     userRepository;
    private final BertScoreClient    bertScoreClient;
    private final ObjectMapper       objectMapper;

    private User resolveUser(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
    }

    @Override
    @Transactional
    public AttemptDto submit(UUID attemptId, SubmitAnswersRequest request, String studentKeycloakId) {
        User student = resolveUser(studentKeycloakId);
        Attempt attempt = attemptRepository.findById(attemptId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tentative introuvable"));

        if (!attempt.getStudent().getId().equals(student.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non autorisé");

        if (attempt.getAttemptStatus() == AttemptStatus.SUBMITTED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette tentative a déjà été soumise");

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(attempt.getSession().getEndTime())) {
            attempt.setAttemptStatus(AttemptStatus.EXPIRED);
            attemptRepository.save(attempt);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Le temps de la session est écoulé");
        }

        List<Question> questions = questionRepository.findByQuiz_IdOrderByPositionAsc(
            attempt.getSession().getQuiz().getId());

        Map<String, String> answers = request.answers();

        // ── 1. Score QCM — exact match (insensible à la casse + trim) ─────────
        int qcmScore = 0;
        List<Question> openQuestions = new ArrayList<>();

        for (Question q : questions) {
            switch (q.getType()) {
                case QCM -> {
                    String given = answers.get(q.getId().toString());
                    if (given != null && given.trim().equalsIgnoreCase(q.getCorrectAnswer().trim()))
                        qcmScore++;
                }
                case OUVERTE, EXERCICE -> openQuestions.add(q);
            }
        }

        // ── 2. Score BERTScore pour les questions ouvertes ────────────────────
        // Le documentId est passé pour récupérer l'evidence RAG (passage source ChromaDB)
        // qui enrichit chaque résultat BERTScore avec un passage explicatif
        String documentId = null;
        try {
            documentId = attempt.getSession().getQuiz().getDocument().getId().toString();
        } catch (Exception e) {
            log.debug("Impossible de récupérer le documentId pour l'evidence RAG : {}", e.getMessage());
        }

        List<BertScoreDetailDto> bertDetails = List.of();
        double openScore = 0.0;

        if (!openQuestions.isEmpty()) {
            bertDetails = bertScoreClient.scoreOpenAnswers(openQuestions, answers, documentId);

            // Score partiel BERTScore : somme des partial_score pour chaque question ouverte
            openScore = bertDetails.stream()
                .mapToDouble(BertScoreDetailDto::partialScore)
                .sum();

            log.info("BERTScore {} questions ouvertes — score partiel total={:.2f}",
                openQuestions.size(), openScore);
        }

        // ── 3. Score final — QCM entiers + BERTScore partiels (arrondi) ───────
        int totalScore = qcmScore + (int) Math.round(openScore);

        // ── 4. Persistance des détails BERTScore en JSONB ─────────────────────
        Map<String, Object> bertDetailsJson = null;
        if (!bertDetails.isEmpty()) {
            bertDetailsJson = new LinkedHashMap<>();
            for (BertScoreDetailDto d : bertDetails) {
                // Utiliser LinkedHashMap pour tolérer les valeurs null (Map.of() les interdit)
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("questionContent", d.questionContent());
                entry.put("studentAnswer",   d.studentAnswer());
                entry.put("referenceAnswer", d.referenceAnswer());
                entry.put("f1",              d.f1());
                entry.put("precision",       d.precision());
                entry.put("recall",          d.recall());
                entry.put("partialScore",    d.partialScore());
                entry.put("label",           d.label());
                entry.put("model",           d.model());
                entry.put("evidenceChunk",   d.evidenceChunk());  // peut être null
                bertDetailsJson.put(d.questionId(), entry);
            }
        }

        attempt.setAnswers(answers);
        attempt.setScore(totalScore);
        attempt.setMaxScore(questions.size());
        attempt.setBertScoreDetails(bertDetailsJson);
        attempt.setAttemptStatus(AttemptStatus.SUBMITTED);
        attempt.setCompletedAt(now);
        attempt = attemptRepository.save(attempt);

        log.info("Tentative {} soumise — score={}/{} (QCM={}, openPartial={:.2f})",
            attemptId, totalScore, questions.size(), qcmScore, openScore);

        return AttemptDto.from(attempt, bertDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptDto getById(UUID attemptId, String requesterKeycloakId) {
        User requester = resolveUser(requesterKeycloakId);
        Attempt attempt = attemptRepository.findById(attemptId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tentative introuvable"));

        boolean isStudent = attempt.getStudent().getId().equals(requester.getId());
        boolean isTeacher = attempt.getSession().getQuiz().getTeacher().getId().equals(requester.getId());
        if (!isStudent && !isTeacher)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non autorisé");

        // Reconstruire les BertScoreDetailDto depuis le JSONB stocké
        List<BertScoreDetailDto> bertDetails = extractBertDetails(attempt);
        return AttemptDto.from(attempt, bertDetails);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttemptDto> listForStudent(String studentKeycloakId, Pageable pageable) {
        User student = resolveUser(studentKeycloakId);
        Page<Attempt> page = attemptRepository.findByStudent_IdOrderByStartedAtDesc(student.getId(), pageable);
        return PageResponse.of(page.map(AttemptDto::from));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<BertScoreDetailDto> extractBertDetails(Attempt attempt) {
        Map<String, Object> raw = attempt.getBertScoreDetails();
        if (raw == null || raw.isEmpty()) return List.of();

        List<BertScoreDetailDto> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> m)) continue;
            Map<String, Object> d = (Map<String, Object>) m;
            // Reconstruire evidenceChunk depuis le JSONB (peut être null si ChromaDB était indisponible)
            @SuppressWarnings("unchecked")
            Map<String, Object> evidenceChunk = (Map<String, Object>) d.get("evidenceChunk");

            result.add(new BertScoreDetailDto(
                entry.getKey(),
                str(d, "questionContent"),
                str(d, "studentAnswer"),
                str(d, "referenceAnswer"),
                dbl(d, "f1"),
                dbl(d, "precision"),
                dbl(d, "recall"),
                dbl(d, "partialScore"),
                str(d, "label"),
                str(d, "model"),
                evidenceChunk
            ));
        }
        return result;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v != null ? v.toString() : "";
    }

    private static double dbl(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0.0; }
    }
}
