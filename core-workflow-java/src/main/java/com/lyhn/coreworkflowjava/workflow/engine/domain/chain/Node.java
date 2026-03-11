package com.lyhn.coreworkflowjava.workflow.engine.domain.chain;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeStatusEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
@Data
@NoArgsConstructor
@AllArgsConstructor
// 在整个工作流引擎中，Node 是最小的执行单元。它描述了：自己是谁、有哪些输入输出、跟哪些节点有关联、当前执行状态是啥、被执行了几次
public class Node {
    /**
     * Node ID in format: "node-type::sequenceId"
     * Examples: "node-start::001", "node-llm::002", "node-plugin::003", "node-end::004"
     */
    @JsonProperty("id")
    private String id;

    /**
     * Node data containing configuration and parameters
     */
    @JsonProperty("data")
    private NodeData data;

    private NodeStatusEnum status;

    /**
     * 前置Node，前面的这些node都执行完毕之后，才会执行当前node
     */
    private List<Node> preNodes;

    /**
     * 后置Node，当前node执行成功之后，执行后续的node
     */
    private List<Node> nextNodes;

    /**
     * 失败Node，当前node执行失败之后，执行后续的node
     */
    private List<Node> failNodes;

    /**
     * 当前node已经执行了多少次
     */
    private AtomicInteger executedCount;

    /**
     * Extract node type from ID
     *
     * @return node type (e.g., "node-start", "node-llm", "node-plugin", "node-end")
     */
    public NodeTypeEnum getNodeType() {
        if (id != null && id.contains("::")) {
            return NodeTypeEnum.fromValue(id.split("::")[0]);
        }
        return null;
    }

    public void init() {
        status = NodeStatusEnum.INIT;
        preNodes = new ArrayList<>();
        nextNodes = new ArrayList<>();
        failNodes = new ArrayList<>();
        executedCount = new AtomicInteger(0);
    }
}