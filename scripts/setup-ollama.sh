#!/bin/bash
# ============================================================
# QuizGen — Script de setup Ollama + Mistral
# Exécuter après docker-compose up :
#   chmod +x scripts/setup-ollama.sh && ./scripts/setup-ollama.sh
# ============================================================

echo "⏳ Attente du démarrage d'Ollama..."
until curl -sf http://localhost:11434/api/tags > /dev/null 2>&1; do
    sleep 2
done
echo "✅ Ollama est prêt"

echo "⏳ Téléchargement du modèle Mistral..."
docker exec quizgen-ollama ollama pull mistral

echo "✅ Modèle Mistral installé"
echo ""
echo "🔍 Vérification :"
docker exec quizgen-ollama ollama list
