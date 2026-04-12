package com.lyhn.coreworkflowjava.workflow.engine.langgraph;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Configuration
public class LangGraphConfig {

    @Bean
    public RedisCheckpointSaver redisCheckpointSaver(StringRedisTemplate stringRedisTemplate) {
        log.info("[LangGraphConfig] Initializing RedisCheckpointSaver");
        return new RedisCheckpointSaver(stringRedisTemplate);
    }
}
