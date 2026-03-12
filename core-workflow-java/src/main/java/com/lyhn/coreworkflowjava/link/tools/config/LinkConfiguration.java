package com.lyhn.coreworkflowjava.link.tools.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@ComponentScan(basePackages = "com.lyhn.coreworkflowjava.link")
public class LinkConfiguration {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        // 设置序列化等配置
        // 设置键的序列化器
        template.setKeySerializer(new StringRedisSerializer());
        // 设置值的序列化器
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        // 设置哈希键的序列化器
        template.setHashKeySerializer(new StringRedisSerializer());
        // 设置哈希值的序列化器
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));

        template.afterPropertiesSet();
        return template;
    }
}