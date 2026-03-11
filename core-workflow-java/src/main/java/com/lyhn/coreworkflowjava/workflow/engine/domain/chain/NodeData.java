package com.lyhn.coreworkflowjava.workflow.engine.domain.chain;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeData {

    // 节点执行前要准备的所有输入参数
    /**
     * Input items
     */
    @JsonProperty("inputs")
    private List<InputItem> inputs = new ArrayList<>();

    // 元信息，主要用于 UI 展示，包括节点名字、图标、说明文案、画布坐标等
    /**
     * Node metadata
     */
    @JsonProperty("nodeMeta")
    private NodeMeta nodeMeta;

    // 执行时需要的参数，这是每个节点独立的配置区域
    /**
     * Node-specific parameters (flexible structure for different node types)
     */
    @JsonProperty("nodeParam")
    private Map<String, Object> nodeParam = new HashMap<>();

    // 节点执行完的输出结果
    /**
     * Output items
     */
    @JsonProperty("outputs")
    private List<OutputItem> outputs = new ArrayList<>();

    // 执行时需要的参数，这是每个节点独立的配置区域
    @JsonProperty("retryConfig")
    private RetryConfig retryConfig;

}
