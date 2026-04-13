package com.lyhn.coreworkflowjava.workflow.engine.integration.mcp;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class McpToolAdapter {

    private final McpToolExecutor mcpToolExecutor;
    private final OkHttpClientPool httpClientPool;

    public McpToolAdapter(McpToolExecutor mcpToolExecutor, OkHttpClientPool httpClientPool) {
        this.mcpToolExecutor = mcpToolExecutor;
        this.httpClientPool = httpClientPool;
    }

    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) throws Exception {
        if (mcpToolExecutor.hasTool(toolName)) {
            log.info("[McpToolAdapter] Routing tool '{}' via MCP protocol", toolName);
            return mcpToolExecutor.executeTool(toolName, arguments);
        }

        log.warn("[McpToolAdapter] Tool '{}' not found in MCP registry, falling back to HTTP", toolName);
        throw new UnsupportedOperationException("Tool not found: " + toolName + ". Consider registering it as an MCP tool.");
    }

    public List<McpToolDefinition> listAvailableTools() {
        return mcpToolExecutor.getAllTools();
    }

    public Map<String, Object> getToolSchema(String toolName) {
        Optional<McpToolDefinition> toolOpt = mcpToolExecutor.getToolDefinition(toolName);
        if (toolOpt.isEmpty()) {
            return Collections.emptyMap();
        }

        McpToolDefinition tool = toolOpt.get();
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("name", tool.getName());
        schema.put("description", tool.getDescription());
        schema.put("inputSchema", tool.getInputSchema());
        schema.put("serverId", tool.getServerId());
        schema.put("transportType", tool.getTransportType().name());
        return schema;
    }

    public OkHttpClientPool getHttpClientPool() {
        return httpClientPool;
    }

    public McpToolExecutor getMcpToolExecutor() {
        return mcpToolExecutor;
    }
}
