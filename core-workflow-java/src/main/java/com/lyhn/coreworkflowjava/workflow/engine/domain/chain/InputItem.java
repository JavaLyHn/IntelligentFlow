package com.lyhn.coreworkflowjava.workflow.engine.domain.chain;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InputItem {
    
    @JsonProperty("id")
    private String id;
    
    /**
     * Input name (e.g., "user_input", "text")
     */
    @JsonProperty("name")
    private String name;
    
    /**
     * Input schema definition
     */
    @JsonProperty("schema")
    private InputSchema schema;
}
