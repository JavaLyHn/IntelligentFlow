package com.lyhn.coreworkflowjava.workflow.engine.langgraph;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class RedisCheckpointSaver implements BaseCheckpointSaver {

    private static final String KEY_PREFIX = "langgraph:checkpoint:";
    private static final long DEFAULT_TTL_SECONDS = 3600;

    private final StringRedisTemplate redisTemplate;

    public RedisCheckpointSaver(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        String threadId = threadId(config);
        String key = buildKey(threadId);
        try {
            Set<Object> fields = redisTemplate.opsForHash().keys(key);
            if (fields == null || fields.isEmpty()) {
                return Collections.emptyList();
            }

            List<Checkpoint> checkpoints = new ArrayList<>();
            for (Object field : fields) {
                String json = (String) redisTemplate.opsForHash().get(key, field);
                if (json != null) {
                    Checkpoint checkpoint = deserializeCheckpoint(json);
                    if (checkpoint != null) {
                        checkpoints.add(checkpoint);
                    }
                }
            }

            return checkpoints;
        } catch (Exception e) {
            log.error("[RedisCheckpointSaver] Failed to list checkpoints: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        Collection<Checkpoint> checkpoints = list(config);
        if (checkpoints.isEmpty()) {
            return Optional.empty();
        }
        return checkpoints.stream().reduce((first, second) -> second);
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        String threadId = threadId(config);
        String key = buildKey(threadId);
        String field = checkpoint.getNodeId() != null ? checkpoint.getNodeId() : UUID.randomUUID().toString();
        try {
            String json = serializeCheckpoint(checkpoint);
            redisTemplate.opsForHash().put(key, field, json);
            redisTemplate.expire(key, java.time.Duration.ofSeconds(DEFAULT_TTL_SECONDS));
            log.debug("[RedisCheckpointSaver] Saved checkpoint: threadId={}, nodeId={}",
                    threadId, checkpoint.getNodeId());
        } catch (Exception e) {
            log.error("[RedisCheckpointSaver] Failed to save checkpoint: {}", e.getMessage(), e);
        }
        return config;
    }

    @Override
    public Tag release(RunnableConfig config) throws Exception {
        return new Tag(threadId(config), Collections.emptyList());
    }

    private String buildKey(String threadId) {
        return KEY_PREFIX + threadId;
    }

    private String serializeCheckpoint(Checkpoint checkpoint) {
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", checkpoint.getId());
            data.put("nodeId", checkpoint.getNodeId());
            data.put("nextNodeId", checkpoint.getNextNodeId());
            data.put("state", checkpoint.getState());
            data.put("createdAt", System.currentTimeMillis());
            return JSON.toJSONString(data);
        } catch (Exception e) {
            log.error("[RedisCheckpointSaver] Failed to serialize checkpoint: {}", e.getMessage());
            return null;
        }
    }

    private Checkpoint deserializeCheckpoint(String json) {
        try {
            Map<String, Object> data = JSON.parseObject(json, Map.class);
            Checkpoint.Builder builder = Checkpoint.builder();
            if (data.get("id") != null) {
                builder.id((String) data.get("id"));
            }
            if (data.get("nodeId") != null) {
                builder.nodeId((String) data.get("nodeId"));
            }
            if (data.get("nextNodeId") != null) {
                builder.nextNodeId((String) data.get("nextNodeId"));
            }
            if (data.get("state") != null) {
                builder.state((Map<String, Object>) data.get("state"));
            }
            return builder.build();
        } catch (Exception e) {
            log.error("[RedisCheckpointSaver] Failed to deserialize checkpoint: {}", e.getMessage());
            return null;
        }
    }
}
