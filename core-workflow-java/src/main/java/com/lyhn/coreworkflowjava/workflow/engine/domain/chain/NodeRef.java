package com.lyhn.coreworkflowjava.workflow.engine.domain.chain;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeRef {
    
    /**
     * ID of the referenced node (e.g., "node-llm::002")
     */
    @JsonProperty("nodeId")
    private String nodeId;
    
    /**
     * Name of the output variable (e.g., "llm_output")
     */
    @JsonProperty("name")
    private String name;
}
