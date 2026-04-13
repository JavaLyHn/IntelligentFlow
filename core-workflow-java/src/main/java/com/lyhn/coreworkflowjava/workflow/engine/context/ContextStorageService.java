package com.lyhn.coreworkflowjava.workflow.engine.context;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ContextStorageService {

    private static final String REDIS_KEY_PREFIX = "ctx:compressed:";
    private static final String REDIS_ENTRY_PREFIX = "ctx:entry:";
    private static final long DEFAULT_TTL_SECONDS = 7200;
    private static final String MINIO_CONTEXT_PREFIX = "context/";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_TEXT = "text/plain";

    private final StringRedisTemplate redisTemplate;
    private final S3ClientAdapter s3Client;
    private final long ttlSeconds;

    public ContextStorageService(StringRedisTemplate redisTemplate,
                                 S3ClientAdapter s3Client,
                                 long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.s3Client = s3Client;
        this.ttlSeconds = ttlSeconds;
    }

    public ContextStorageService(StringRedisTemplate redisTemplate,
                                 S3ClientAdapter s3Client) {
        this(redisTemplate, s3Client, DEFAULT_TTL_SECONDS);
    }

    public void saveCompressedContext(CompressedContext context) {
        String key = REDIS_KEY_PREFIX + context.getSessionId();
        try {
            String json = JSON.toJSONString(context);
            redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
            log.debug("[ContextStorageService] Saved compressed context: sessionId={}, entries={}",
                    context.getSessionId(), context.getEntryCount());
        } catch (Exception e) {
            log.error("[ContextStorageService] Failed to save compressed context: {}", e.getMessage(), e);
            throw new ContextStorageException("Failed to save compressed context", e);
        }
    }

    public CompressedContext loadCompressedContext(String sessionId) {
        String key = REDIS_KEY_PREFIX + sessionId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return JSON.parseObject(json, CompressedContext.class);
        } catch (Exception e) {
            log.error("[ContextStorageService] Failed to load compressed context: {}", e.getMessage(), e);
            throw new ContextStorageException("Failed to load compressed context", e);
        }
    }

    public void saveEntrySummary(ContextEntry entry) {
        String key = REDIS_ENTRY_PREFIX + entry.getSessionId() + ":" + entry.getEntryId();
        try {
            String json = JSON.toJSONString(entry);
            redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("[ContextStorageService] Failed to save entry summary: {}", e.getMessage(), e);
        }
    }

    public ContextEntry loadEntrySummary(String sessionId, String entryId) {
        String key = REDIS_ENTRY_PREFIX + sessionId + ":" + entryId;
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return JSON.parseObject(json, ContextEntry.class);
        } catch (Exception e) {
            log.error("[ContextStorageService] Failed to load entry summary: {}", e.getMessage(), e);
            return null;
        }
    }

    public String externalizeToMinIO(String sessionId, String entryId, String content) {
        String objectKey = MINIO_CONTEXT_PREFIX + sessionId + "/" + entryId + ".json";
        try {
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            String url = s3Client.uploadObject(objectKey, CONTENT_TYPE_JSON, data);
            log.info("[ContextStorageService] Externalized content to MinIO: sessionId={}, entryId={}, size={}",
                    sessionId, entryId, data.length);
            return objectKey;
        } catch (Exception e) {
            log.error("[ContextStorageService] Failed to externalize to MinIO: {}", e.getMessage(), e);
            throw new ContextStorageException("Failed to externalize content to MinIO", e);
        }
    }

    public String readFromMinIO(String storagePath) {
        try {
            InputStream is = s3Client.getObject(storagePath);
            if (is == null) {
                log.warn("[ContextStorageService] Content not found in MinIO: {}", storagePath);
                return null;
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();
            return content;
        } catch (Exception e) {
            log.error("[ContextStorageService] Failed to read from MinIO: {}", e.getMessage(), e);
            throw new ContextStorageException("Failed to read content from MinIO", e);
        }
    }

    public ContextFragment readFragmentFromMinIO(String storagePath, String sessionId,
                                                  String entryId, int startOffset, int endOffset) {
        String fullContent = readFromMinIO(storagePath);
        if (fullContent == null) {
            return null;
        }

        int safeStart = Math.max(0, startOffset);
        int safeEnd = Math.min(fullContent.length(), endOffset);

        if (safeStart >= safeEnd) {
            return ContextFragment.of(entryId, sessionId, safeStart, safeStart, "");
        }

        String fragmentContent = fullContent.substring(safeStart, safeEnd);
        return ContextFragment.of(entryId, sessionId, safeStart, safeEnd, fragmentContent);
    }

    public boolean deleteFromMinIO(String storagePath) {
        try {
            s3Client.deleteObject(storagePath);
            log.info("[ContextStorageService] Deleted from MinIO: {}", storagePath);
            return true;
        } catch (Exception e) {
            log.error("[ContextStorageService] Failed to delete from MinIO: {}", e.getMessage(), e);
            return false;
        }
    }

    public void deleteCompressedContext(String sessionId) {
        String key = REDIS_KEY_PREFIX + sessionId;
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("[ContextStorageService] Failed to delete compressed context: {}", e.getMessage(), e);
        }
    }

    public boolean contextExists(String sessionId) {
        String key = REDIS_KEY_PREFIX + sessionId;
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("[ContextStorageService] Failed to check context existence: {}", e.getMessage(), e);
            return false;
        }
    }

    public static class ContextStorageException extends RuntimeException {
        public ContextStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
