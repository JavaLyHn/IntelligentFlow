package com.lyhn.coreworkflowjava.workflow.engine.domain.chain;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Value {
    
    /**
     * Type of the value: "ref" or "literal"
     */
    @JsonProperty("type")
    private String type;
    
    /**
     * Content can be NodeRef (for type="ref") or primitive value (for type="literal")
     */
    @JsonProperty("content")
    private Object content;
    
    public boolean isReference() {
        return "ref".equals(type);
    }
    
    public NodeRef getNodeRef() {
        if (isReference() && content instanceof NodeRef) {
            return (NodeRef) content;
        }
        return null;
    }
}
