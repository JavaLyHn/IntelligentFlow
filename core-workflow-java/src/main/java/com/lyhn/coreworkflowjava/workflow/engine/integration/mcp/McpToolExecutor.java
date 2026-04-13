package com.lyhn.coreworkflowjava.workflow.engine.integration.mcp;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class McpToolExecutor {

    private final McpConnectionPool connectionPool;
    private final ConcurrentHashMap<String, McpToolDefinition> toolRegistry = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<McpToolDefinition>> serverToolIndex = new ConcurrentHashMap<>();

    public McpToolExecutor(McpConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public void registerServer(McpServerConfig config) {
        connectionPool.registerServer(config);

        if (config.getTools() != null) {
            for (McpToolDefinition tool : config.getTools()) {
                tool.setServerId(config.getServerId());
                tool.setServerUrl(config.getServerUrl());
                toolRegistry.put(tool.getName(), tool);
                serverToolIndex.computeIfAbsent(config.getServerId(), k -> new ArrayList<>()).add(tool);
            }
        }

        log.info("[McpToolExecutor] Registered server: id={}, tools={}",
                config.getServerId(), config.getTools() != null ? config.getTools().size() : 0);
    }

    public void discoverAndRegisterTools(String serverId) throws IOException {
        McpSseClient client = null;
        try {
            client = connectionPool.getConnection(serverId);
            List<McpToolDefinition> tools = client.listTools();

            for (McpToolDefinition tool : tools) {
                toolRegistry.put(tool.getName(), tool);
            }
            serverToolIndex.put(serverId, tools);

            log.info("[McpToolExecutor] Discovered {} tools from server: {}", tools.size(), serverId);
        } finally {
            if (client != null) {
                connectionPool.releaseConnection(serverId, client);
            }
        }
    }

    public Map<String, Object> executeTool(String toolName, Map<String, Object> arguments) throws IOException {
        McpToolDefinition toolDef = toolRegistry.get(toolName);
        if (toolDef == null) {
            throw new IOException("Tool not found: " + toolName);
        }

        if (!toolDef.isEnabled()) {
            throw new IOException("Tool is disabled: " + toolName);
        }

        String serverId = toolDef.getServerId();
        McpSseClient client = null;
        try {
            long startTime = System.currentTimeMillis();
            client = connectionPool.getConnection(serverId);

            Map<String, Object> result = client.callTool(toolName, arguments);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[McpToolExecutor] Tool '{}' executed on server '{}' in {}ms",
                    toolName, serverId, elapsed);

            return result;
        } catch (IOException e) {
            log.error("[McpToolExecutor] Tool '{}' execution failed: {}", toolName, e.getMessage());
            throw e;
        } finally {
            if (client != null) {
                connectionPool.releaseConnection(serverId, client);
            }
        }
    }

    public Optional<McpToolDefinition> getToolDefinition(String toolName) {
        return Optional.ofNullable(toolRegistry.get(toolName));
    }

    public List<McpToolDefinition> getAllTools() {
        return new ArrayList<>(toolRegistry.values());
    }

    public List<McpToolDefinition> getToolsByServer(String serverId) {
        return serverToolIndex.getOrDefault(serverId, Collections.emptyList());
    }

    public boolean hasTool(String toolName) {
        return toolRegistry.containsKey(toolName);
    }

    public void unregisterServer(String serverId) {
        List<McpToolDefinition> tools = serverToolIndex.remove(serverId);
        if (tools != null) {
            for (McpToolDefinition tool : tools) {
                toolRegistry.remove(tool.getName());
            }
        }
        connectionPool.unregisterServer(serverId);
        log.info("[McpToolExecutor] Unregistered server: {}", serverId);
    }

    public int getToolCount() {
        return toolRegistry.size();
    }

    public int getServerCount() {
        return connectionPool.getRegisteredServerCount();
    }

    public McpConnectionPool.PoolStats getConnectionStats(String serverId) {
        return connectionPool.getStats(serverId);
    }
}
