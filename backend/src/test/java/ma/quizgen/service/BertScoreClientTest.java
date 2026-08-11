package ma.quizgen.service;

import ma.quizgen.dto.BertScoreDetailDto;
import ma.quizgen.entity.Question;
import ma.quizgen.entity.enums.Difficulty;
import ma.quizgen.entity.enums.QuestionType;
import ma.quizgen.service.impl.BertScoreClientImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BertScoreClient — appels NLP et fallback")
class BertScoreClientTest {

    @Mock
    private RestTemplate restTemplate;

    private BertScoreClientImpl client;

    private static final String NLP_URL = "http://nlp-service:8000";

    @BeforeEach
    void setUp() {
        client = new BertScoreClientImpl(restTemplate, NLP_URL);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Question openQuestion(String id, String content, String correctAnswer) {
        Question q = new Question();
        q.setId(UUID.fromString(id));
        q.setContent(content);
        q.setCorrectAnswer(correctAnswer);
        q.setType(QuestionType.OUVERTE);
        q.setDifficulty(Difficulty.MOYEN);
        q.setPosition(0);
        return q;
    }

    private ResponseEntity<Map> successResponse(List<Map<String, Object>> results) {
        Map<String, Object> body = new HashMap<>();
        body.put("results", results);
        body.put("total_items", results.size());
        body.put("available", true);
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> scoreResult(String questionId, double f1, double partial, String label) {
        Map<String, Object> r = new HashMap<>();
        r.put("question_id", questionId);
        r.put("f1", f1);
        r.put("precision", f1 + 0.02);
        r.put("recall", f1 - 0.02);
        r.put("partial_score", partial);
        r.put("label", label);
        r.put("model", "roberta-large");
        return r;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Retourne une liste vide si aucune question ouverte fournie")
    void scoreOpenAnswers_emptyQuestions_returnsEmptyList() {
        var result = client.scoreOpenAnswers(List.of(), Map.of());
        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Retourne une liste vide si toutes les réponses sont vides")
    void scoreOpenAnswers_allAnswersBlank_returnsEmptyList() {
        var q = openQuestion("00000000-0000-0000-0000-000000000001",
                             "Définissez la photosynthèse.",
                             "Synthèse de matière organique par les plantes.");
        var result = client.scoreOpenAnswers(List.of(q), Map.of());
        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Appelle le service NLP et mappe correctement les résultats CORRECT")
    void scoreOpenAnswers_correctAnswer_returnsMappedResult() {
        String qId = "00000000-0000-0000-0000-000000000001";
        var q = openQuestion(qId, "Définissez la photosynthèse.",
                             "Synthèse de matière organique par les plantes.");
        Map<String, String> answers = Map.of(qId, "La photosynthèse est la conversion de lumière en sucre.");

        when(restTemplate.exchange(
            eq(NLP_URL + "/score/batch"),
            eq(HttpMethod.POST),
            any(HttpEntity.class),
            eq(Map.class)
        )).thenReturn(successResponse(List.of(
            scoreResult(qId, 0.82, 1.0, "CORRECT")
        )));

        var results = client.scoreOpenAnswers(List.of(q), answers);

        assertThat(results).hasSize(1);
        BertScoreDetailDto dto = results.get(0);
        assertThat(dto.questionId()).isEqualTo(qId);
        assertThat(dto.f1()).isEqualTo(0.82);
        assertThat(dto.partialScore()).isEqualTo(1.0);
        assertThat(dto.label()).isEqualTo("CORRECT");
        assertThat(dto.model()).isEqualTo("roberta-large");
        assertThat(dto.studentAnswer()).isEqualTo(answers.get(qId));
        assertThat(dto.referenceAnswer()).isEqualTo(q.getCorrectAnswer());
    }

    @Test
    @DisplayName("Retourne un label PARTIEL pour F1 dans [0.50, 0.70[")
    void scoreOpenAnswers_partialAnswer_returnsMappedPartielResult() {
        String qId = "00000000-0000-0000-0000-000000000002";
        var q = openQuestion(qId, "Qu'est-ce que la mitose ?",
                             "Division cellulaire produisant deux cellules identiques.");
        Map<String, String> answers = Map.of(qId, "Division des cellules.");

        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
            .thenReturn(successResponse(List.of(scoreResult(qId, 0.60, 0.5, "PARTIEL"))));

        var results = client.scoreOpenAnswers(List.of(q), answers);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).label()).isEqualTo("PARTIEL");
        assertThat(results.get(0).partialScore()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Retourne un fallback si le service NLP est indisponible")
    void scoreOpenAnswers_nlpServiceDown_returnsFallbackWithZeroScore() {
        String qId = "00000000-0000-0000-0000-000000000003";
        var q = openQuestion(qId, "Décrivez l'ATP.", "Molécule d'énergie cellulaire.");
        Map<String, String> answers = Map.of(qId, "Adénosine triphosphate.");

        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
            .thenThrow(new ResourceAccessException("Connection refused"));

        var results = client.scoreOpenAnswers(List.of(q), answers);

        assertThat(results).hasSize(1);
        BertScoreDetailDto dto = results.get(0);
        assertThat(dto.f1()).isEqualTo(0.0);
        assertThat(dto.partialScore()).isEqualTo(0.0);
        assertThat(dto.label()).isEqualTo("INCORRECT");
        assertThat(dto.model()).isEqualTo("unavailable");
        // L'information de la question est quand même incluse
        assertThat(dto.questionId()).isEqualTo(qId);
        assertThat(dto.studentAnswer()).isEqualTo("Adénosine triphosphate.");
    }

    @Test
    @DisplayName("Envoie le bon payload JSON au service NLP")
    void scoreOpenAnswers_sendsCorrectPayload() {
        String qId = "00000000-0000-0000-0000-000000000004";
        var q = openQuestion(qId, "Qu'est-ce que la respiration cellulaire ?",
                             "Dégradation du glucose en ATP.");
        Map<String, String> answers = Map.of(qId, "Production d'énergie par le glucose.");

        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
            .thenReturn(successResponse(List.of(scoreResult(qId, 0.72, 1.0, "CORRECT"))));

        client.scoreOpenAnswers(List.of(q), answers);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor =
            ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
            eq(NLP_URL + "/score/batch"),
            eq(HttpMethod.POST),
            captor.capture(),
            eq(Map.class)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> body = captor.getValue().getBody();
        assertThat(body).containsKey("items");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsEntry("question_id", qId);
        assertThat(items.get(0)).containsEntry("candidate", "Production d'énergie par le glucose.");
        assertThat(items.get(0)).containsEntry("reference", "Dégradation du glucose en ATP.");

        // Vérifier les headers Content-Type
        assertThat(captor.getValue().getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("Gère plusieurs questions ouvertes en batch")
    void scoreOpenAnswers_multipleQuestions_returnsAllResults() {
        String q1Id = "00000000-0000-0000-0000-000000000005";
        String q2Id = "00000000-0000-0000-0000-000000000006";

        var q1 = openQuestion(q1Id, "Q1", "Ref1");
        var q2 = openQuestion(q2Id, "Q2", "Ref2");
        Map<String, String> answers = Map.of(q1Id, "Ans1", q2Id, "Ans2");

        when(restTemplate.exchange(anyString(), any(), any(), eq(Map.class)))
            .thenReturn(successResponse(List.of(
                scoreResult(q1Id, 0.80, 1.0, "CORRECT"),
                scoreResult(q2Id, 0.40, 0.0, "INCORRECT")
            )));

        var results = client.scoreOpenAnswers(List.of(q1, q2), answers);

        assertThat(results).hasSize(2);
        assertThat(results.stream().map(BertScoreDetailDto::questionId))
            .containsExactlyInAnyOrder(q1Id, q2Id);
    }
}
