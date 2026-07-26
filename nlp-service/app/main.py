"""QuizGen — NLP Service (FastAPI)"""

from fastapi import FastAPI

app = FastAPI(
    title="QuizGen NLP Service",
    description="Microservice IA : extraction PDF, génération de questions, correction sémantique",
    version="0.1.0",
)


@app.get("/health")
async def health():
    return {"status": "ok", "service": "quizgen-nlp"}


@app.get("/")
async def root():
    return {"message": "QuizGen NLP Service is running"}
