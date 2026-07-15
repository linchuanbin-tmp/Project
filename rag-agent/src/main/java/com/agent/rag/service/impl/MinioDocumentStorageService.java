package com.agent.rag.service.impl;

import com.agent.rag.dto.StoredDocument;
import com.agent.rag.service.DocumentStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinioDocumentStorageService implements DocumentStorageService {

    @Value("${rag.storage.minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${rag.storage.minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${rag.storage.minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${rag.storage.minio.bucket:rag-documents}")
    private String bucket;

    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Override
    public StoredDocument storeOriginal(Long kbId, Long sourceDocumentId, String fileName, String contentType, byte[] content) {
        try {
            ensureBucket();
            String objectKey = buildObjectKey(kbId, sourceDocumentId, fileName);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(new ByteArrayInputStream(content), content.length, -1)
                            .contentType(contentType != null && !contentType.isBlank()
                                    ? contentType
                                    : "application/octet-stream")
                            .build()
            );
            return StoredDocument.builder()
                    .provider("minio")
                    .bucket(bucket)
                    .objectKey(objectKey)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store document in MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] readOriginal(String objectKey) {
        return readOriginal(bucket, objectKey);
    }

    @Override
    public byte[] readOriginal(String bucket, String objectKey) {
        String targetBucket = bucket == null || bucket.isBlank() ? this.bucket : bucket;
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(targetBucket)
                        .object(objectKey)
                        .build()
        )) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read document from MinIO: " + e.getMessage(), e);
        }
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(bucket)
                        .build()
        );
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(bucket)
                            .build()
            );
        }
    }

    private String buildObjectKey(Long kbId, Long sourceDocumentId, String fileName) {
        String safeFileName = fileName == null || fileName.isBlank()
                ? "document"
                : fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        return String.format(
                Locale.ROOT,
                "kb/%d/source/%d/original/%s_%s",
                kbId,
                sourceDocumentId,
                UUID.randomUUID(),
                safeFileName
        );
    }
}
