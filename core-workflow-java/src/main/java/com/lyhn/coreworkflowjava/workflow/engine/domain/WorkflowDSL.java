package com.lyhn.coreworkflowjava.workflow.engine.domain;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Edge;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Node;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDSL {
    // 业务主键
    /**
     * Workflow ID
     */
    private String flowId;

    // 做调度幂等控制（比如你前端同时发起同一个流程多次，系统只执行一次）
    /**
     * Workflow UUID
     */
    private String uuid;

    // 一个流程至少要包含开始节点、结束节点和若干业务节点
    /**
     * List of nodes in the workflow
     */
    @JsonProperty("nodes")
    private List<Node> nodes = new ArrayList<>();

    // 节点之间的连接关系
    /**
     * List of edges connecting nodes
     */
    @JsonProperty("edges")
    private List<Edge> edges = new ArrayList<>();


}