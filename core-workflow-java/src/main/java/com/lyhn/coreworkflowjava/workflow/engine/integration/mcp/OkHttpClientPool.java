package com.lyhn.coreworkflowjava.workflow.engine.integration.mcp;

import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class OkHttpClientPool {

    private final int maxIdleConnections;
    private final long keepAliveDurationMs;
    private final long defaultConnectTimeoutMs;
    private final long defaultReadTimeoutMs;
    private final long defaultWriteTimeoutMs;

    private final ConnectionPool sharedConnectionPool;
    private final ConcurrentHashMap<String, OkHttpClient> clientCache = new ConcurrentHashMap<>();

    private volatile OkHttpClient defaultClient;

    public OkHttpClientPool(int maxIdleConnections, long keepAliveDurationMs,
                            long defaultConnectTimeoutMs, long defaultReadTimeoutMs,
                            long defaultWriteTimeoutMs) {
        this.maxIdleConnections = maxIdleConnections;
        this.keepAliveDurationMs = keepAliveDurationMs;
        this.defaultConnectTimeoutMs = defaultConnectTimeoutMs;
        this.defaultReadTimeoutMs = defaultReadTimeoutMs;
        this.defaultWriteTimeoutMs = defaultWriteTimeoutMs;

        this.sharedConnectionPool = new ConnectionPool(
                maxIdleConnections, keepAliveDurationMs, TimeUnit.MILLISECONDS);

        this.defaultClient = createClient(defaultConnectTimeoutMs, defaultReadTimeoutMs, defaultWriteTimeoutMs);

        log.info("[OkHttpClientPool] Initialized: maxIdle={}, keepAlive={}ms, connTimeout={}ms, readTimeout={}ms",
                maxIdleConnections, keepAliveDurationMs, defaultConnectTimeoutMs, defaultReadTimeoutMs);
    }

    public OkHttpClientPool() {
        this(20, 300000, 30000, 300000, 300000);
    }

    public OkHttpClient getClient() {
        return defaultClient;
    }

    public OkHttpClient getClient(String tag) {
        return clientCache.computeIfAbsent(tag, t -> {
            log.debug("[OkHttpClientPool] Creating OkHttpClient for tag: {}", t);
            return createClient(defaultConnectTimeoutMs, defaultReadTimeoutMs, defaultWriteTimeoutMs);
        });
    }

    public OkHttpClient getClientWithTimeout(long connectTimeoutMs, long readTimeoutMs) {
        return createClient(connectTimeoutMs, readTimeoutMs, defaultWriteTimeoutMs);
    }

    private OkHttpClient createClient(long connectTimeoutMs, long readTimeoutMs, long writeTimeoutMs) {
        return new OkHttpClient.Builder()
                .connectionPool(sharedConnectionPool)
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMs, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public int getTotalConnectionCount() {
        return sharedConnectionPool.connectionCount();
    }

    public int getIdleConnectionCount() {
        return sharedConnectionPool.idleConnectionCount();
    }

    public void evictAll() {
        sharedConnectionPool.evictAll();
        log.info("[OkHttpClientPool] Evicted all idle connections");
    }

    public void shutdown() {
        log.info("[OkHttpClientPool] Shutting down client pool");
        clientCache.clear();
        sharedConnectionPool.evictAll();
        defaultClient.dispatcher().executorService().shutdownNow();
        log.info("[OkHttpClientPool] Client pool shut down");
    }

    public PoolInfo getPoolInfo() {
        return new PoolInfo(
                sharedConnectionPool.connectionCount(),
                sharedConnectionPool.idleConnectionCount(),
                clientCache.size(),
                maxIdleConnections,
                keepAliveDurationMs
        );
    }

    public record PoolInfo(
            int totalConnections,
            int idleConnections,
            int clientCount,
            int maxIdleConnections,
            long keepAliveDurationMs
    ) {}
}
