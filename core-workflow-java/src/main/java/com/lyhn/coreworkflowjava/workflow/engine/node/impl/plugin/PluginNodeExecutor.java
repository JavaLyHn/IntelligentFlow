package com.lyhn.coreworkflowjava.workflow.engine.node.impl.plugin;

import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeExecStatusEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeRunResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeState;
import com.lyhn.coreworkflowjava.workflow.engine.integration.plugins.PluginServiceClient;
import com.lyhn.coreworkflowjava.workflow.engine.node.AbstractNodeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
// 节点生命周期管理、上下文处理、异常兜底、回调这些通用逻辑都由 AbstractNodeExecutor 兜着
public class PluginNodeExecutor extends AbstractNodeExecutor {
    @Autowired
    private PluginServiceClient pluginServiceClient;

    // 告诉执行引擎自己是 PLUGIN 插件节点执行器
    @Override
    public NodeTypeEnum getNodeType() {
        return NodeTypeEnum.PLUGIN;
    }

    // 把执行请求交给真正干活的 PluginServiceClient
    @Override
    protected NodeRunResult executeNode(NodeState nodeState, Map<String, Object> inputs) throws Exception {
        Map<String, Object> outputs = pluginServiceClient.toolCall(nodeState, inputs);
        NodeRunResult result = new NodeRunResult();
        result.setOutputs(outputs);
        result.setInputs(inputs);
        result.setStatus(NodeExecStatusEnum.SUCCESS);
        return result;
    }
}