FROM python:3.11-slim

WORKDIR /app

# Dépendances système pour PyMuPDF et SpaCy
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    g++ \
    libffi-dev \
    libmupdf-dev \
    && rm -rf /var/lib/apt/lists/*

# Dépendances Python
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Modèle SpaCy français (large)
RUN python -m spacy download fr_core_news_lg

# Modèle d'embeddings KeyBERT (sentence-transformers, ~450 Mo).
# Pré-téléchargé au build plutôt qu'au premier appel Celery : le pipeline
# de génération a un hard time limit de 660s (voir celery_app.py / tasks.py),
# et un téléchargement à froid dans ce délai peut échouer selon la bande
# passante disponible au moment de l'exécution — ce qui bloquait
# indéfiniment toute génération de quiz.
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('paraphrase-multilingual-MiniLM-L12-v2')"

# Code source
COPY . .

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
