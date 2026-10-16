package ma.quizgen.dto;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Diagnostic pédagogique d'une copie, généré par le LLM configuré. */
public record RecommendationDto(
    String summary,
    List<String> strengths,
    List<String> weaknesses,
    List<String> focusAreas,
    List<String> actions,
    String model,
    LocalDateTime generatedAt
) {
    /** Depuis la réponse du microservice NLP (clés snake_case). */
    public static RecommendationDto fromNlp(Map<String, Object> nlp, LocalDateTime generatedAt) {
        return new RecommendationDto(
            text(nlp.get("summary")),
            list(nlp.get("strengths")),
            list(nlp.get("weaknesses")),
            list(nlp.get("focus_areas")),
            list(nlp.get("actions")),
            text(nlp.get("model")),
            generatedAt
        );
    }

    /** Depuis la colonne JSONB attempts.recommendation. */
    public static RecommendationDto fromStored(Map<String, Object> stored, LocalDateTime generatedAt) {
        if (stored == null) return null;
        return new RecommendationDto(
            text(stored.get("summary")),
            list(stored.get("strengths")),
            list(stored.get("weaknesses")),
            list(stored.get("focusAreas")),
            list(stored.get("actions")),
            text(stored.get("model")),
            generatedAt
        );
    }

    public Map<String, Object> toStored() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", summary);
        m.put("strengths", strengths);
        m.put("weaknesses", weaknesses);
        m.put("focusAreas", focusAreas);
        m.put("actions", actions);
        m.put("model", model);
        return m;
    }

    private static String text(Object o) {
        return o == null ? null : o.toString();
    }

    private static List<String> list(Object o) {
        if (!(o instanceof List<?> l)) return List.of();
        return l.stream().filter(v -> v != null).map(Object::toString).toList();
    }
}
