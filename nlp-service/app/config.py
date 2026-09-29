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
    ollama_timeout: int = 300  # secondes — inférence CPU lente, un appel = 1 chunk
    ollama_num_ctx: int = 4096      # prompt CoT + chunk + contexte RAG > 2048 tokens
    ollama_keep_alive: str = "30m"  # garder le modèle chargé entre les appels

    # Celery — budget temps d'une génération complète
    generation_soft_time_limit: int = 1200  # secondes
    generation_time_limit: int = 1260       # secondes (kill dur)
    # Au-delà, on arrête d'appeler le LLM et on renvoie les questions déjà obtenues
    generation_llm_budget: int = 1000       # secondes
    # Une tâche restée plus longtemps en file est jetée (évite de rejouer un backlog périmé)
    generation_queue_expires: int = 900     # secondes

    # NLP
    spacy_model: str = "fr_core_news_lg"
    keybert_top_k: int = 10
    section_max_tokens: int = 500

    # LLM — fournisseur (voir app/llm_providers.py)
    llm_mode: str = "offline"       # offline = Ollama local | online = API distante
    llm_model: str = ""             # modèle en ligne (ex. claude-haiku-4-5-20251001)
    llm_api_key: str = ""
    llm_api_base_url: str = ""      # ex. https://api.anthropic.com, https://api.openai.com/v1
    llm_api_timeout: int = 120      # secondes
    llm_max_tokens: int = 1024      # 1-2 questions + reasoning tiennent largement
    llm_max_retries: int = 3
    llm_temperature: float = 0.3

    # Post-processing
    rouge_dedup_threshold: float = 0.8

    # RAG — ChromaDB vector store
    rag_enabled: bool = True
    rag_top_k: int = 3
    chromadb_path: str = "/tmp/chroma"

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        extra = "ignore"

    def get_celery_backend(self) -> str:
        return self.celery_result_backend or self.redis_url


@lru_cache()
def get_settings() -> Settings:
    return Settings()
