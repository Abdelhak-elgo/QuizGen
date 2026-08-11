"""Configuration centralisée via variables d'environnement."""
from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    # Redis / Celery
    redis_url: str = "redis://:redis_secret_2025@localhost:6379/0"
    celery_result_backend: str = ""  # defaults to redis_url if empty

    # MinIO
    minio_url: str = "http://localhost:9000"
    minio_access_key: str = "minioadmin"
    minio_secret_key: str = "minio_secret_2025"
    minio_bucket_documents: str = "documents"

    # Ollama
    ollama_url: str = "http://localhost:11434"
    ollama_model: str = "mistral"
    ollama_timeout: int = 120  # secondes

    # NLP
    spacy_model: str = "fr_core_news_lg"
    keybert_top_k: int = 10
    section_max_tokens: int = 500

    # LLM
    llm_max_retries: int = 3
    llm_temperature: float = 0.3

    # Post-processing
    rouge_dedup_threshold: float = 0.8

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        extra = "ignore"

    def get_celery_backend(self) -> str:
        return self.celery_result_backend or self.redis_url


@lru_cache()
def get_settings() -> Settings:
    return Settings()
