"""Client MinIO pour télécharger les fichiers PDF stockés."""
import io
import logging
from typing import IO

from minio import Minio
from minio.error import S3Error

from app.config import get_settings

logger = logging.getLogger(__name__)
settings = get_settings()


def _build_client() -> Minio:
    """Construit un client MinIO à partir de la configuration."""
    parsed = settings.minio_url.replace("http://", "").replace("https://", "")
    secure = settings.minio_url.startswith("https://")
    return Minio(
        endpoint=parsed,
        access_key=settings.minio_access_key,
        secret_key=settings.minio_secret_key,
        secure=secure,
    )


def download_pdf(bucket_name: str, object_key: str) -> IO[bytes]:
    """
    Télécharge un objet PDF depuis MinIO et le retourne en tant que flux binaire.

    Args:
        bucket_name: Nom du bucket MinIO.
        object_key:  Clé de l'objet (chemin dans le bucket).

    Returns:
        BytesIO contenant le contenu du PDF.

    Raises:
        FileNotFoundError: Si l'objet n'existe pas dans MinIO.
        RuntimeError:      En cas d'erreur d'accès à MinIO.
    """
    client = _build_client()
    try:
        response = client.get_object(bucket_name, object_key)
        data = response.read()
        response.close()
        response.release_conn()
        logger.info(
            "PDF téléchargé depuis MinIO : %s/%s (%d octets)",
            bucket_name, object_key, len(data),
        )
        return io.BytesIO(data)
    except S3Error as exc:
        if exc.code == "NoSuchKey":
            raise FileNotFoundError(
                f"Objet introuvable dans MinIO : {bucket_name}/{object_key}"
            ) from exc
        raise RuntimeError(
            f"Erreur MinIO ({exc.code}) pour {bucket_name}/{object_key} : {exc.message}"
        ) from exc
    except Exception as exc:
        raise RuntimeError(f"Impossible de se connecter à MinIO : {exc}") from exc
