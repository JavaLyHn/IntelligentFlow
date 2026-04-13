package com.lyhn.coreworkflowjava.workflow.engine.context;

import com.lyhn.coreworkflowjava.workflow.engine.util.S3ClientUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;

@Slf4j
public class S3ClientAdapter {

    private final S3ClientUtil s3ClientUtil;

    public S3ClientAdapter(S3ClientUtil s3ClientUtil) {
        this.s3ClientUtil = s3ClientUtil;
    }

    public String uploadObject(String objectKey, String contentType, byte[] data) {
        return s3ClientUtil.uploadObject(objectKey, contentType, data);
    }

    public String uploadObject(String objectKey, String contentType, InputStream inputStream) {
        return s3ClientUtil.uploadObject(objectKey, contentType, inputStream);
    }

    public InputStream getObject(String objectKey) {
        try {
            String bucket = s3ClientUtil.getDefaultBucket();
            io.minio.MinioClient minioClient = getMinioClient();
            if (minioClient == null) {
                log.warn("[S3ClientAdapter] MinioClient not available");
                return null;
            }
            return minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            log.error("[S3ClientAdapter] Failed to get object: {}", e.getMessage(), e);
            return null;
        }
    }

    public void deleteObject(String objectKey) {
        try {
            String bucket = s3ClientUtil.getDefaultBucket();
            io.minio.MinioClient minioClient = getMinioClient();
            if (minioClient == null) {
                log.warn("[S3ClientAdapter] MinioClient not available");
                return;
            }
            minioClient.removeObject(
                    io.minio.RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            log.error("[S3ClientAdapter] Failed to delete object: {}", e.getMessage(), e);
        }
    }

    private io.minio.MinioClient getMinioClient() {
        try {
            java.lang.reflect.Field field = S3ClientUtil.class.getDeclaredField("minioClient");
            field.setAccessible(true);
            return (io.minio.MinioClient) field.get(s3ClientUtil);
        } catch (Exception e) {
            log.error("[S3ClientAdapter] Failed to access MinioClient: {}", e.getMessage());
            return null;
        }
    }
}
