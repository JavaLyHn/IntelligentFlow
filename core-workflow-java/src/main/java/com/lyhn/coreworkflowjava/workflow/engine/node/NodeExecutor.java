package com.lyhn.coreworkflowjava.workflow.engine.node;

import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeRunResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeState;

public interface NodeExecutor {
    // NodeExecutor 接口只定义了一个核心方法 execute，用于触发节点的执行逻辑
    NodeRunResult execute(NodeState nodeState) throws Exception;

    // 声明当前执行器所能处理的节点类型
    NodeTypeEnum getNodeType();
}