package com.lyhn.coreworkflowjava.workflow.engine.integration.mcp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "workflow.mcp.enabled", havingValue = "true", matchIfMissing = true)
public class McpAutoConfiguration {

    @Value("${workflow.mcp.pool.max-connections-per-server:5}")
    private int maxConnectionsPerServer;

    @Value("${workflow.mcp.pool.connection-timeout-ms:30000}")
    private long connectionTimeoutMs;

    @Value("${workflow.mcp.pool.read-timeout-ms:300000}")
    private long readTimeoutMs;

    @Value("${workflow.mcp.pool.idle-timeout-ms:300000}")
    private long idleTimeoutMs;

    @Value("${workflow.mcp.pool.eviction-interval-ms:60000}")
    private long evictionIntervalMs;

    @Value("${workflow.mcp.http-pool.max-idle-connections:20}")
    private int httpMaxIdleConnections;

    @Value("${workflow.mcp.http-pool.keep-alive-duration-ms:300000}")
    private long httpKeepAliveDurationMs;

    @Bean
    @ConditionalOnMissingBean
    public OkHttpClientPool okHttpClientPool() {
        log.info("[McpAutoConfiguration] Creating OkHttpClientPool: maxIdle={}, keepAlive={}ms",
                httpMaxIdleConnections, httpKeepAliveDurationMs);
        return new OkHttpClientPool(
                httpMaxIdleConnections,
                httpKeepAliveDurationMs,
                connectionTimeoutMs,
                readTimeoutMs,
                readTimeoutMs
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public McpConnectionPool mcpConnectionPool() {
        log.info("[McpAutoConfiguration] Creating McpConnectionPool: maxPerServer={}, connTimeout={}ms",
                maxConnectionsPerServer, connectionTimeoutMs);
        return new McpConnectionPool(
                maxConnectionsPerServer,
                connectionTimeoutMs,
                readTimeoutMs,
                idleTimeoutMs,
                evictionIntervalMs
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public McpToolExecutor mcpToolExecutor(McpConnectionPool connectionPool) {
        log.info("[McpAutoConfiguration] Creating McpToolExecutor");
        return new McpToolExecutor(connectionPool);
    }

    @Bean
    @ConditionalOnMissingBean
    public McpToolAdapter mcpToolAdapter(McpToolExecutor mcpToolExecutor, OkHttpClientPool httpClientPool) {
        log.info("[McpAutoConfiguration] Creating McpToolAdapter");
        return new McpToolAdapter(mcpToolExecutor, httpClientPool);
    }
}
