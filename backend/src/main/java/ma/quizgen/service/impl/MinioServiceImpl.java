package ma.quizgen.service.impl;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.quizgen.service.MinioService;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;

    @Override
    public String uploadFile(String bucket, String objectKey,
                             InputStream inputStream, String contentType, long fileSize) {
        try {
            ensureBucketExists(bucket);

            ObjectWriteResponse response = minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, fileSize, -1)
                    .contentType(contentType)
                    .build()
            );

            log.info("Fichier uploadé vers MinIO: {}/{} (etag={})",
                bucket, objectKey, response.etag());
            return response.etag();

        } catch (Exception e) {
            log.error("Erreur upload MinIO: {}/{}", bucket, objectKey, e);
            throw new RuntimeException("Erreur lors de l'upload vers MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public String getPresignedUrl(String bucket, String objectKey, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build()
            );
        } catch (Exception e) {
            log.error("Erreur génération URL présignée: {}/{}", bucket, objectKey, e);
            throw new RuntimeException("Erreur URL présignée: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteObject(String bucket, String objectKey) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build()
            );
            log.info("Objet supprimé de MinIO: {}/{}", bucket, objectKey);
        } catch (Exception e) {
            log.error("Erreur suppression MinIO: {}/{}", bucket, objectKey, e);
            throw new RuntimeException("Erreur suppression MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public void ensureBucketExists(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build()
            );
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Bucket MinIO créé: {}", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("Impossible de créer le bucket MinIO: " + bucket, e);
        }
    }
}
