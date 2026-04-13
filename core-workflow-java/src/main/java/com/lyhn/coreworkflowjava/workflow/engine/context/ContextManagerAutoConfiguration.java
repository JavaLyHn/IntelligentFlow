package com.lyhn.coreworkflowjava.workflow.engine.context;

import com.lyhn.coreworkflowjava.workflow.engine.util.S3ClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "workflow.context.manager.enabled", havingValue = "true", matchIfMissing = true)
public class ContextManagerAutoConfiguration {

    @Autowired(required = false)
    private S3ClientUtil s3ClientUtil;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${workflow.context.manager.compression-threshold:2000}")
    private int compressionThreshold;

    @Value("${workflow.context.manager.summary-max-length:200}")
    private int summaryMaxLength;

    @Value("${workflow.context.manager.ttl-seconds:7200}")
    private long ttlSeconds;

    @Bean
    @ConditionalOnMissingBean
    public S3ClientAdapter s3ClientAdapter() {
        if (s3ClientUtil != null) {
            log.info("[ContextManagerAutoConfiguration] Creating S3ClientAdapter with S3ClientUtil");
            return new S3ClientAdapter(s3ClientUtil);
        }
        log.info("[ContextManagerAutoConfiguration] S3ClientUtil not available, creating no-op adapter");
        return new S3ClientAdapter(null);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextStorageService contextStorageService(S3ClientAdapter s3ClientAdapter) {
        log.info("[ContextManagerAutoConfiguration] Creating ContextStorageService");

        if (stringRedisTemplate == null) {
            log.warn("[ContextManagerAutoConfiguration] StringRedisTemplate not available, context persistence will be limited");
        }

        return new ContextStorageService(stringRedisTemplate, s3ClientAdapter, ttlSeconds);
    }

    @Bean
    @ConditionalOnMissingBean
    public ContextManager contextManager(ContextStorageService contextStorageService) {
        log.info("[ContextManagerAutoConfiguration] Creating ContextManager with threshold={}, summaryMax={}",
                compressionThreshold, summaryMaxLength);
        return new ContextManager(contextStorageService, compressionThreshold, summaryMaxLength);
    }
}
