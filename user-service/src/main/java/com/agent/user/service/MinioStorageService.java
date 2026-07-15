package com.agent.user.service;

import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Value("${minio.bucket}")
    private String bucket;

    private MinioClient client;

    @PostConstruct
    public void init() {
        client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        ensureBucket();
    }

    private void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.error("Failed to ensure MinIO bucket {}: {}", bucket, e.getMessage());
        }
    }

    /**
     * Upload a file to MinIO. Returns the object key.
     */
    public String upload(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String objectKey = UUID.randomUUID().toString().replace("-", "") + ext;

        try (InputStream is = file.getInputStream()) {
            client.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(is, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("Uploaded file to MinIO: {} ({} bytes)", objectKey, file.getSize());
            return objectKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * Get a pre-signed download URL valid for the given duration.
     */
    public String getPresignedUrl(String objectKey, int expiryMinutes) {
        try {
            return client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate download URL: " + e.getMessage(), e);
        }
    }

    /**
     * Get file as InputStream for direct download.
     */
    public InputStream download(String objectKey) {
        try {
            return client.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from MinIO: " + e.getMessage(), e);
        }
    }

    /**
     * Get file content type.
     */
    public String getContentType(String objectKey) {
        try {
            StatObjectResponse stat = client.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            return stat.contentType();
        } catch (Exception e) {
            return "application/octet-stream";
        }
    }

    /**
     * Delete a file from MinIO. Does not throw if the file doesn't exist.
     */
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        try {
            client.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            log.info("Deleted file from MinIO: {}", objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete MinIO object {}: {}", objectKey, e.getMessage());
        }
    }
}
