package com.lyhn.coreworkflowjava.workflow.engine.integration.mcp;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class McpConnectionPool {

    private final int maxConnectionsPerServer;
    private final long connectionTimeoutMs;
    private final long readTimeoutMs;
    private final long idleTimeoutMs;
    private final long evictionIntervalMs;

    private final ConcurrentHashMap<String,Deque<McpSseClient>> availableConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> activeConnectionCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, McpServerConfig> serverConfigs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> serverLocks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService evictionScheduler;
    private volatile boolean shutdown = false;

    private okhttp3.OkHttpClient sharedHttpClient;

    public McpConnectionPool(int maxConnectionsPerServer, long connectionTimeoutMs,
                             long readTimeoutMs, long idleTimeoutMs, long evictionIntervalMs) {
        this.maxConnectionsPerServer = maxConnectionsPerServer;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.idleTimeoutMs = idleTimeoutMs;
        this.evictionIntervalMs = evictionIntervalMs;

        this.sharedHttpClient = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(connectionTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .connectionPool(new okhttp3.ConnectionPool(maxConnectionsPerServer * 4, 5, TimeUnit.MINUTES))
                .build();

        this.evictionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-pool-evictor");
            t.setDaemon(true);
            return t;
        });

        this.evictionScheduler.scheduleAtFixedRate(
                this::evictIdleConnections, evictionIntervalMs, evictionIntervalMs, TimeUnit.MILLISECONDS);

        log.info("[McpConnectionPool] Initialized: maxPerServer={}, connTimeout={}ms, readTimeout={}ms, idleTimeout={}ms",
                maxConnectionsPerServer, connectionTimeoutMs, readTimeoutMs, idleTimeoutMs);
    }

    public McpConnectionPool() {
        this(5, 30000, 300000, 300000, 60000);
    }

    public void registerServer(McpServerConfig config) {
        if (config == null || config.getServerUrl() == null) {
            throw new IllegalArgumentException("Server config and URL must not be null");
        }

        String key = config.getConnectionPoolKey();
        serverConfigs.put(key, config);
        availableConnections.putIfAbsent(key, new ConcurrentLinkedDeque<>());
        activeConnectionCount.putIfAbsent(key, new AtomicInteger(0));
        serverLocks.putIfAbsent(key, new ReentrantLock());

        log.info("[McpConnectionPool] Registered server: id={}, url={}, maxConn={}",
                config.getServerId(), config.getServerUrl(), config.getMaxConnections());
    }

    public void unregisterServer(String serverId) {
        String key = findKeyByServerId(serverId);
        if (key == null) {
            log.warn("[McpConnectionPool] Server not found for unregistration: {}", serverId);
            return;
        }

        Deque<McpSseClient> connections = availableConnections.remove(key);
        if (connections != null) {
            for (McpSseClient client : connections) {
                client.disconnect();
            }
        }
        serverConfigs.remove(key);
        activeConnectionCount.remove(key);
        serverLocks.remove(key);

        log.info("[McpConnectionPool] Unregistered server: {}", serverId);
    }

    public McpSseClient getConnection(String serverId) throws IOException {
        if (shutdown) {
            throw new IllegalStateException("Connection pool is shut down");
        }

        String key = findKeyByServerId(serverId);
        if (key == null) {
            throw new IOException("Server not registered: " + serverId);
        }

        McpServerConfig config = serverConfigs.get(key);
        Deque<McpSseClient> pool = availableConnections.get(key);
        ReentrantLock lock = serverLocks.get(key);

        lock.lock();
        try {
            while (!pool.isEmpty()) {
                McpSseClient client = pool.pollFirst();
                if (client != null && client.isConnected()) {
                    log.debug("[McpConnectionPool] Reusing connection for server: {}", serverId);
                    return client;
                } else if (client != null) {
                    client.disconnect();
                    activeConnectionCount.get(key).decrementAndGet();
                }
            }

            int currentCount = activeConnectionCount.get(key).get();
            int maxConn = config != null ? config.getMaxConnections() : maxConnectionsPerServer;

            if (currentCount < maxConn) {
                activeConnectionCount.get(key).incrementAndGet();
                lock.unlock();

                try {
                    McpSseClient newClient = createConnection(config);
                    log.debug("[McpConnectionPool] Created new connection for server: {}", serverId);
                    return newClient;
                } catch (IOException e) {
                    activeConnectionCount.get(key).decrementAndGet();
                    throw e;
                }
            } else {
                lock.unlock();
                log.warn("[McpConnectionPool] Max connections reached for server: {}, waiting...", serverId);

                try {
                    return waitForConnection(key, 30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for connection");
                }
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public void releaseConnection(String serverId, McpSseClient client) {
        if (client == null || shutdown) {
            return;
        }

        String key = findKeyByServerId(serverId);

        if (client.isConnected()) {
            Deque<McpSseClient> pool = availableConnections.get(key);
            if (pool != null) {
                pool.offerFirst(client);
                log.debug("[McpConnectionPool] Released connection for server: {}", serverId);
                return;
            }
        }

        client.disconnect();
        if (key != null) {
            activeConnectionCount.get(key).decrementAndGet();
        }
        log.debug("[McpConnectionPool] Discarded stale connection for server: {}", serverId);
    }

    private McpSseClient waitForConnection(String key, long timeout, TimeUnit unit) throws InterruptedException, IOException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        Deque<McpSseClient> pool = availableConnections.get(key);

        while (System.currentTimeMillis() < deadline) {
            McpSseClient client = pool.pollFirst();
            if (client != null && client.isConnected()) {
                return client;
            } else if (client != null) {
                client.disconnect();
                activeConnectionCount.get(key).decrementAndGet();
            }
            Thread.sleep(100);
        }

        throw new IOException("Timeout waiting for available connection to server");
    }

    private McpSseClient createConnection(McpServerConfig config) throws IOException {
        McpSseClient client = new McpSseClient(
                config.getServerId(),
                config.getServerUrl(),
                sharedHttpClient
        );
        client.connect();
        return client;
    }

    private void evictIdleConnections() {
        if (shutdown) {
            return;
        }

        long now = System.currentTimeMillis();

        for (Map.Entry<String, Deque<McpSseClient>> entry : availableConnections.entrySet()) {
            String key = entry.getKey();
            Deque<McpSseClient> pool = entry.getValue();

            Iterator<McpSseClient> it = pool.iterator();
            while (it.hasNext()) {
                McpSseClient client = it.next();
                long idleTime = now - client.getLastActivityTime();

                if (idleTime > idleTimeoutMs || !client.isConnected()) {
                    it.remove();
                    client.disconnect();
                    activeConnectionCount.get(key).decrementAndGet();
                    log.debug("[McpConnectionPool] Evicted idle connection for server: {}", client.getServerId());
                }
            }
        }
    }

    private String findKeyByServerId(String serverId) {
        for (Map.Entry<String, McpServerConfig> entry : serverConfigs.entrySet()) {
            if (entry.getValue().getServerId().equals(serverId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public PoolStats getStats(String serverId) {
        String key = findKeyByServerId(serverId);
        if (key == null) {
            return new PoolStats(0, 0, 0);
        }

        Deque<McpSseClient> pool = availableConnections.get(key);
        AtomicInteger count = activeConnectionCount.get(key);

        return new PoolStats(
                count != null ? count.get() : 0,
                pool != null ? pool.size() : 0,
                serverConfigs.size()
        );
    }

    public int getRegisteredServerCount() {
        return serverConfigs.size();
    }

    public List<String> getRegisteredServerIds() {
        return serverConfigs.values().stream()
                .map(McpServerConfig::getServerId)
                .toList();
    }

    public void shutdown() {
        log.info("[McpConnectionPool] Shutting down connection pool");
        shutdown = true;
        evictionScheduler.shutdownNow();

        for (Map.Entry<String, Deque<McpSseClient>> entry : availableConnections.entrySet()) {
            for (McpSseClient client : entry.getValue()) {
                client.disconnect();
            }
        }
        availableConnections.clear();
        activeConnectionCount.clear();
        serverConfigs.clear();
        serverLocks.clear();

        if (sharedHttpClient != null) {
            sharedHttpClient.dispatcher().executorService().shutdownNow();
            sharedHttpClient.connectionPool().evictAll();
        }

        log.info("[McpConnectionPool] Connection pool shut down");
    }

    public record PoolStats(int activeConnections, int availableConnections, int totalServers) {}
}
