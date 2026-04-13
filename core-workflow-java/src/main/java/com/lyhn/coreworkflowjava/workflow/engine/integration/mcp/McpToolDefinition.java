package com.lyhn.coreworkflowjava.workflow.engine.integration.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    private String description;

    private Map<String, Object> inputSchema;

    private String serverId;

    private String serverUrl;

    private String serverName;

    @Builder.Default
    private McpTransportType transportType = McpTransportType.SSE;

    @Builder.Default
    private boolean enabled = true;

    public enum McpTransportType {
        SSE,
        STREAMABLE_HTTP
    }

    public boolean hasRequiredFields() {
        return name != null && !name.isEmpty()
                && serverUrl != null && !serverUrl.isEmpty();
    }
}
