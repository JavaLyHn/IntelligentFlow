package com.lyhn.coreworkflowjava.workflow.engine;

import com.lyhn.coreworkflowjava.workflow.engine.constants.EndNodeOutputModeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.context.EngineContextHolder;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeRunResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.WorkflowDSL;
import com.lyhn.coreworkflowjava.workflow.engine.domain.callbacks.ChatCallBackStreamResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.callbacks.LLMGenerate;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Node;
import com.lyhn.coreworkflowjava.workflow.engine.node.NodeExecutor;
import com.lyhn.coreworkflowjava.workflow.engine.node.callback.WorkflowMsgCallback;
import com.lyhn.coreworkflowjava.workflow.engine.util.FlowUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 负责调度和执行链路
 */
@Slf4j
@Component
public class WorkflowEngine {
    private final Map<NodeTypeEnum, NodeExecutor> nodeExecutors;

    public WorkflowEngine(List<NodeExecutor> executors) {
        this.nodeExecutors = new HashMap<>();
        for (NodeExecutor executor : executors) {
            this.nodeExecutors.put(executor.getNodeType(), executor);
        }
        log.info("Registered {} node executors: {}", nodeExecutors.size(), nodeExecutors.keySet());
    }

    public void execute(WorkflowDSL workflowDSL, VariablePool variablePool, Map<String, Object> inputs, StreamCallback callback) throws Exception {
        log.info("Starting workflow execution with {} nodes", workflowDSL.getNodes().size());

        // 前置校验
        verifyWorkflow(workflowDSL);

        // 清空上下文变量
        variablePool.clear();

        // 创建工作流回调处理器
        Queue<ChatCallBackStreamResult> orderStreamResultQ = new ConcurrentLinkedQueue<>();
        Queue<LLMGenerate> streamQueue = new ConcurrentLinkedQueue<>();

        Node endNode = workflowDSL.getNodes().stream().filter(s -> s.getNodeType() == NodeTypeEnum.END).findFirst().get();
        String sid = FlowUtil.genWorkflowId(workflowDSL.getFlowId());
        WorkflowMsgCallback workflowCallback = new WorkflowMsgCallback(
                sid,
                callback,
                Objects.equals(endNode.getData().getNodeParam().get("outputMode"), 1) ? EndNodeOutputModeEnum.VARIABLE_MODE : EndNodeOutputModeEnum.DIRECT_MODE,
                streamQueue,
                orderStreamResultQ
        );

        // 初始化上下文
        EngineContextHolder.initContext(workflowDSL.getFlowId(), workflowDSL.getUuid(), workflowCallback);

        // 发送工作流开始事件
        workflowCallback.onWorkflowStart();

        try{
            // 构建从起始节点开始的执行链路
            Node startNode = buildNodeExecuteChain(workflowDSL);

            // 初始化启动参数
            initializeStartNodeInputs(startNode, variablePool, inputs);

            // 执行编排的流程节点
            executeNode(startNode, variablePool, workflowCallback);

            log.info("Workflow: {} execution completed successfully", sid);
            // 发送工作流结束事件
            workflowCallback.onWorkflowEnd(new NodeRunResult()); // 这里应该传入实际的结果
        } catch (Exception e) {
            // 发送工作流错误结束事件
            workflowCallback.onWorkflowEnd(new NodeRunResult());
            throw e;
        } finally {
            // 消费所有的消息
            workflowCallback.finished();
            // 移除上下文信息
            EngineContextHolder.remove();
        }
    }


}