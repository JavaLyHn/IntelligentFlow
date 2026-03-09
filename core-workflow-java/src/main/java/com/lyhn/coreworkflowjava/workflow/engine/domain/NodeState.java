package com.lyhn.coreworkflowjava.workflow.engine.domain;

import com.lyhn.coreworkflowjava.workflow.engine.VariablePool;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Node;
import com.lyhn.coreworkflowjava.workflow.engine.node.callback.WorkflowMsgCallback;

public record NodeState(Node node, VariablePool variablePool, WorkflowMsgCallback callback) {
    // 当前要执行的节点
    // 变量池，用于存储和获取节点之间传递的数据
    // 回调接口，用来把执行过程里的状态变化、输出内容实时传递出去
}
