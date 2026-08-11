#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# QuizGen — Test de performance génération de quiz
# Objectif : mesurer P95 de génération ≤ 15s sous 10 requêtes simultanées
# Usage : bash scripts/performance-test.sh
# Variables :
#   API_BASE_URL    URL de l'API (défaut : http://localhost:8080/api/v1)
#   QUIZGEN_TOKEN   Token Keycloak (requis si auth activée)
#   DOCUMENT_ID     ID d'un document existant à utiliser
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

API_BASE="${API_BASE_URL:-http://localhost:8080/api/v1}"
TOKEN="${QUIZGEN_TOKEN:-}"
DOC_ID="${DOCUMENT_ID:-}"
N_CONCURRENT=10
REPORT_FILE="scripts/performance-report.txt"

echo "QuizGen — Test de performance génération quiz"
echo "API : $API_BASE"
echo "Concurrent : $N_CONCURRENT requêtes"
echo ""

# ── Vérifier que l'API est disponible ─────────────────────────────────────
if ! curl -sf --max-time 5 "${API_BASE}/actuator/health" > /dev/null 2>&1; then
  echo "API non disponible à $API_BASE — test ignoré"
  echo "API non disponible — test ignoré" > "$REPORT_FILE"
  exit 0
fi

# ── Fonction : durée en secondes ────────────────────────────────────────────
measure_time() {
  local start end duration
  start=$(date +%s%3N)
  "$@" > /dev/null 2>&1
  end=$(date +%s%3N)
  echo "scale=3; ($end - $start) / 1000" | bc
}

# ── Headers d'authentification ───────────────────────────────────────────────
AUTH_HEADER=""
if [ -n "$TOKEN" ]; then
  AUTH_HEADER="-H 'Authorization: Bearer $TOKEN'"
fi

# ── Test 1 : health check (baseline) ─────────────────────────────────────────
echo "Test 1 : Health check baseline"
TIMES=()
for i in $(seq 1 5); do
  T=$(measure_time curl -sf "${API_BASE}/actuator/health")
  TIMES+=("$T")
  echo "  [${i}] ${T}s"
done
echo ""

# ── Test 2 : Génération quiz — requêtes parallèles ────────────────────────────
echo "Test 2 : Génération de quiz — $N_CONCURRENT requêtes simultanées"

if [ -z "$DOC_ID" ]; then
  echo "  DOCUMENT_ID non défini — test de génération ignoré"
  echo "  (Définir DOCUMENT_ID avec l'ID d'un document existant)"
else
  TMPDIR=$(mktemp -d)
  PIDS=()
  START_ALL=$(date +%s%3N)

  for i in $(seq 1 $N_CONCURRENT); do
    (
      t0=$(date +%s%3N)
      curl -sf \
        ${AUTH_HEADER:+"-H" "$AUTH_HEADER"} \
        -X POST "${API_BASE}/quizzes/generate" \
        -H "Content-Type: application/json" \
        -d "{\"documentId\":\"$DOC_ID\",\"nbQuestions\":5,\"types\":[\"QCM\"],\"difficulty\":\"FACILE\"}" \
        -o "${TMPDIR}/result_${i}.json" \
        --max-time 60 || echo "{}" > "${TMPDIR}/result_${i}.json"
      t1=$(date +%s%3N)
      echo "scale=3; ($t1 - $t0) / 1000" | bc > "${TMPDIR}/time_${i}.txt"
    ) &
    PIDS+=($!)
  done

  # Attendre toutes les requêtes
  for pid in "${PIDS[@]}"; do
    wait "$pid" || true
  done

  END_ALL=$(date +%s%3N)
  TOTAL_S=$(echo "scale=3; ($END_ALL - $START_ALL) / 1000" | bc)

  # Analyser les durées
  echo ""
  echo "  Résultats :"
  ALL_TIMES=()
  for i in $(seq 1 $N_CONCURRENT); do
    T=$(cat "${TMPDIR}/time_${i}.txt" 2>/dev/null || echo "0")
    ALL_TIMES+=("$T")
    echo "  [${i}] ${T}s"
  done

  # Calculer statistiques
  SORTED=$(printf '%s\n' "${ALL_TIMES[@]}" | sort -n)
  N_TIMES=${#ALL_TIMES[@]}
  IDX_P95=$(echo "scale=0; ($N_TIMES * 95 / 100) - 1" | bc)
  IDX_P95=${IDX_P95:-0}
  P95=$(echo "$SORTED" | sed -n "$((IDX_P95 + 1))p")
  MAX=$(echo "$SORTED" | tail -1)
  AVG=$(printf '%s\n' "${ALL_TIMES[@]}" | awk '{s+=$1}END{printf "%.3f", s/NR}')

  echo ""
  echo "  Statistiques :"
  echo "  Moyenne   : ${AVG}s"
  echo "  P95       : ${P95}s"
  echo "  Max       : ${MAX}s"
  echo "  Durée totale ($N_CONCURRENT //): ${TOTAL_S}s"

  # Objectif : P95 ≤ 15s
  P95_OK=$(echo "$P95 <= 15" | bc 2>/dev/null || echo "0")
  if [ "$P95_OK" = "1" ]; then
    echo "  ✅ P95 (${P95}s) ≤ 15s — OBJECTIF ATTEINT"
  else
    echo "  ❌ P95 (${P95}s) > 15s — OBJECTIF MANQUÉ (optimisation requise)"
  fi

  rm -rf "$TMPDIR"
fi

# ── Rapport ──────────────────────────────────────────────────────────────────
{
  echo "QuizGen — Rapport Performance"
  echo "Date : $(date -u +"%Y-%m-%d %H:%M:%S UTC")"
  echo "API  : $API_BASE"
  echo ""
  echo "Objectif : génération quiz ≤ 15s (P95) sous $N_CONCURRENT requêtes simultanées"
  echo ""
  if [ -n "$DOC_ID" ]; then
    echo "P95 : ${P95}s"
    echo "Max : ${MAX}s"
    echo "Moy : ${AVG}s"
    echo "Statut : $([ "$P95_OK" = "1" ] && echo "✅ OK" || echo "❌ KO")"
  else
    echo "Test de génération ignoré (DOCUMENT_ID non défini)"
  fi
} > "$REPORT_FILE"

echo ""
echo "Rapport : $REPORT_FILE"
