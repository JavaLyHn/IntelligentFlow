package com.lyhn.coreworkflowjava.workflow.engine.domain.chain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
// 定义节点之间的连接关系
public class Edge {
    /**
     * Source node ID
     */
    @JsonProperty("sourceNodeId")
    private String sourceNodeId;

    /**
     * Target node ID
     */
    @JsonProperty("targetNodeId")
    private String targetNodeId;

    // 用于条件判断或插件输出场景
    // 某个节点输出了多个字段（如 LLM 输出 text 和 score）；你希望下游节点只依赖其中一个字段，就可以通过 sourceHandle 精确绑定
    /**
     * Source handle (output name from source node)
     */
    @JsonProperty("sourceHandle")
    private String sourceHandle;

    public String getSource() {
        return sourceNodeId;
    }

    public String getTarget() {
        return targetNodeId;
    }
}