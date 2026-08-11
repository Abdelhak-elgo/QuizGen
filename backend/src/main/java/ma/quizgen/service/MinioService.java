package ma.quizgen.service;

import java.io.InputStream;

public interface MinioService {

    /**
     * Upload un fichier dans MinIO.
     *
     * @param bucket      nom du bucket cible
     * @param objectKey   clé de l'objet (chemin dans le bucket)
     * @param inputStream données du fichier
     * @param contentType MIME type
     * @param fileSize    taille en octets (-1 si inconnue)
     * @return ETag retourné par MinIO
     */
    String uploadFile(String bucket, String objectKey,
                      InputStream inputStream, String contentType, long fileSize);

    /**
     * Génère une URL présignée valable {@code expirySeconds} secondes.
     */
    String getPresignedUrl(String bucket, String objectKey, int expirySeconds);

    /**
     * Supprime un objet de MinIO.
     */
    void deleteObject(String bucket, String objectKey);

    /**
     * Vérifie si le bucket existe, le crée sinon.
     */
    void ensureBucketExists(String bucket);
}
