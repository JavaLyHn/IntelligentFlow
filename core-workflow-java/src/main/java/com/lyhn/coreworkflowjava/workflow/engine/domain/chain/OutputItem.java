package com.lyhn.coreworkflowjava.workflow.engine.domain.chain;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
@Data
@NoArgsConstructor
@AllArgsConstructor
// 每个节点执行完之后，或多或少都有会有一些输出结果 OutputItem，这些结果可能需要展示给前端，可能需要提供给后续节点引用
public class OutputItem {
    
    @JsonProperty("id")
    private String id;
    
    /**
     * Output name (e.g., "llm_output", "voice_url")
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * Output schema definition
     */
    @JsonProperty("schema")
    private Map<String, Object> schema;
    
    /**
     * Whether this output is required
     */
    @JsonProperty("required")
    private boolean required;
}
