package com.lyhn.coreworkflowjava.workflow.engine.domain.chain;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
// 当一个节点需要使用另一个节点的输出结果时，就会用 NodeRef 来表示这种引用关系
/**
 * "value": {
 *   "type": "ref",
 *   "content": {
 *     "nodeId": "spark-llm::123456",
 *     "name": "llm_output"
 *   }
 * }
 */
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
