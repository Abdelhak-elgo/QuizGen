#!/bin/bash
# ──────────────────────────────────────────────────────────────────────────────
# setup-ollama.sh — Télécharge le modèle Mistral dans Ollama
# Usage : chmod +x scripts/setup-ollama.sh && ./scripts/setup-ollama.sh
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
MODEL="${OLLAMA_MODEL:-mistral}"

echo "🔍  Vérification d'Ollama sur ${OLLAMA_URL}..."
until curl -sf "${OLLAMA_URL}/api/tags" > /dev/null; do
    echo "⏳  En attente d'Ollama..."
    sleep 5
done

echo "📦  Téléchargement du modèle ${MODEL} (~4 Go)..."
curl -X POST "${OLLAMA_URL}/api/pull" \
    -H "Content-Type: application/json" \
    -d "{\"name\": \"${MODEL}\"}" \
    --no-buffer | grep -E '"status"' | tail -1

echo "✅  Modèle ${MODEL} prêt dans Ollama."
