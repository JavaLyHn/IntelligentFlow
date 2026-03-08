package com.lyhn.coreworkflowjava.workflow.engine.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.minio.*;
import io.minio.errors.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Component
public class S3ClientUtil {
    @Value("${s3.endpoint}")
    private String endpoint;

    @Value("${s3.remoteEndpoint}")
    private String remoteEndpoint;

    @Value("${s3.accessKey}")
    private String accessKey;

    @Value("${s3.secretKey}")
    private String secretKey;

    @Getter
    @Value("${s3.bucket}")
    private String defaultBucket;

    @Getter
    @Value("${s3.presignExpirySeconds:600}")
    private int presignExpirySeconds;

    @Value("${s3.enablePublicRead:false}")
    private boolean enablePublicRead;

    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        log.info("Minio config - endpoint: {}, remoteEndpoint: {}, defaultBucket: {}, presignExpirySeconds: {}, enablePublicRead: {}",
                endpoint, remoteEndpoint, defaultBucket, presignExpirySeconds, enablePublicRead);

        // Check if default bucket exists, create if not
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(defaultBucket).build());
            if (!found) {
                log.info("Creating S3 bucket: {}", defaultBucket);
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(defaultBucket).build());
                log.info("Created S3 bucket: {}", defaultBucket);
            } else {
                log.info("S3 bucket already exists: {}", defaultBucket);
            }
            // Set bucket policy to public read only if enabled
            if (enablePublicRead) {
                String publicReadPolicy = buildPublicReadPolicy(defaultBucket);
                minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder()
                                .bucket(defaultBucket)
                                .config(publicReadPolicy)
                                .build());
                log.info("Set public read policy for bucket: {}", defaultBucket);
            }
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                 XmlParserException e) {
            log.error("Failed to check/create/configure S3 bucket '{}': {}", defaultBucket, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private String buildPublicReadPolicy(String bucketName) {
        JSONObject policy = new JSONObject();
        policy.put("Version", "2012-10-17");

        JSONObject statement = new JSONObject();
        statement.put("Effect", "Allow");

        JSONObject principal = new JSONObject();
        principal.put("AWS", new JSONArray().fluentAdd("*"));
        statement.put("Principal", principal);

        statement.put("Action", new JSONArray().fluentAdd("s3:GetObject"));
        statement.put("Resource", new JSONArray().fluentAdd(String.format("arn:aws:s3:::%s/*", bucketName)));

        policy.put("Statement", new JSONArray().fluentAdd(statement));

        return policy.toJSONString();
    }

    // 上传object对象
    public String uploadObject(String bucketName, String objectKey, String contentType, InputStream inputStream, long objectSize, long partSize) {
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder().bucket(bucketName).object(objectKey).stream(inputStream, objectSize, partSize);

            if (contentType != null && !contentType.isEmpty()) {
                builder.contentType(contentType);
            }

            minioClient.putObject(builder.build());

            // Build object URL
            return buildObjectUrl(bucketName, objectKey);
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException | ServerException |
                 XmlParserException e) {
            if (log.isErrorEnabled()) {
                log.error("S3 error on upload: {}", e.getMessage(), e);
            }
            throw new RuntimeException(e);
        }
    }

    private String buildObjectUrl(String bucketName, String objectKey) {
        return String.format("%s/%s/%s", remoteEndpoint, bucketName, objectKey);
    }

    public String uploadObject(String objectKey, String contentType, InputStream inputStream, long objectSize, long partSize) {
        return uploadObject(defaultBucket, objectKey, contentType, inputStream, objectSize, partSize);
    }

    // 上传字节数组
    public String uploadObject(String bucketName, String objectKey, String contentType, byte[] data) {
        try (InputStream inputStream = new ByteArrayInputStream(data)) {
            return uploadObject(bucketName, objectKey, contentType, inputStream, data.length, -1);
        } catch (IOException e) {
            // ByteArrayInputStream.close won't throw IOException; present to satisfy try-with-resources
            throw new RuntimeException(e);
        }
    }

    public String uploadObject(String objectKey, String contentType, byte[] data) {
        return uploadObject(defaultBucket, objectKey, contentType, data);
    }

    public String uploadObject(String bucketName, String objectKey, String contentType, InputStream inputStream) {
        // Use -1 as objectSize; MinIO will use multipart upload (recommend 5MB part size)
        return uploadObject(bucketName, objectKey, contentType, inputStream, -1, 5L * 1024 * 1024);
    }

    public String uploadObject(String objectKey, String contentType, InputStream inputStream) {
        return uploadObject(defaultBucket, objectKey, contentType, inputStream);
    }

    // 将预签名 URL 中的内部端点（endpoint）替换为外部可访问的端点（remoteEndpoint）
    private String replaceEndpointInPresignedUrl(String presignedUrl) {
        if (presignedUrl != null && presignedUrl.startsWith(endpoint)) {
            return remoteEndpoint + presignedUrl.substring(endpoint.length());
        }
        return presignedUrl;
    }

    // 生成预签名 PUT URL 的核心功能，主要用于支持浏览器直接上传文件到 S3 存储
    public String generatePresignedPutUrl(String bucketName, String objectKey, int expirySeconds) {
        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.PUT).bucket(bucketName).object(objectKey).expiry(expirySeconds).build());
            return replaceEndpointInPresignedUrl(presignedUrl);
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException | XmlParserException |
                 ServerException e) {
            log.error("S3 error on presign PUT for bucket '{}', object '{}': {}", bucketName, objectKey, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public String generatePresignedPutUrl(String objectKey) {
        return generatePresignedPutUrl(defaultBucket, objectKey, presignExpirySeconds);
    }

    public String generatePresignedGetUrl(String bucketName, String objectKey, int expirySeconds) {
        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(expirySeconds)
                            .build());
            return replaceEndpointInPresignedUrl(presignedUrl);
        } catch (ErrorResponseException | InsufficientDataException | InternalException | InvalidKeyException |
                 InvalidResponseException | IOException | NoSuchAlgorithmException | XmlParserException |
                 ServerException e) {
            log.error("S3 error on presign GET for bucket '{}', object '{}': {}", bucketName, objectKey, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public String generatePresignedGetUrl(String objectKey) {
        return generatePresignedGetUrl(defaultBucket, objectKey, presignExpirySeconds);
    }
}