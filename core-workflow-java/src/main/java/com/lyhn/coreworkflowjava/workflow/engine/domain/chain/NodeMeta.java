package com.lyhn.coreworkflowjava.workflow.engine.domain.chain;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeMeta {
    
    /**
     * Type of the node (e.g., "node-start", "node-llm", "node-plugin", "node-end")
     */
    @JsonProperty("nodeType")
    private String nodeType;
    
    /**
     * Human-readable alias name
     */
    @JsonProperty("aliasName")
    private String aliasName;
}
