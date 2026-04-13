package com.lyhn.coreworkflowjava.workflow.engine.integration.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private String serverId;

    private String serverName;

    private String serverUrl;

    private String brief;

    @Builder.Default
    private McpToolDefinition.McpTransportType transportType = McpToolDefinition.McpTransportType.SSE;

    @Builder.Default
    private boolean authorized = true;

    private List<McpToolDefinition> tools;

    private Map<String, String> envKeys;

    @Builder.Default
    private int maxConnections = 5;

    @Builder.Default
    private long connectionTimeoutMs = 30000;

    @Builder.Default
    private long readTimeoutMs = 300000;

    @Builder.Default
    private long idleTimeoutMs = 300000;

    public String getConnectionPoolKey() {
        return serverId + "@" + serverUrl;
    }
}
