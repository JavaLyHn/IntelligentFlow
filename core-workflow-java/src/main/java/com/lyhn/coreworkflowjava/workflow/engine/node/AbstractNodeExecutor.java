package com.lyhn.coreworkflowjava.workflow.engine.node;

import cn.hutool.core.util.BooleanUtil;
import com.alibaba.fastjson2.JSON;
import com.lyhn.coreworkflowjava.workflow.engine.VariablePool;
import com.lyhn.coreworkflowjava.workflow.engine.constants.ErrorStrategyEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeExecStatusEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeRunResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeState;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.InputItem;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Node;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.RetryConfig;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Value;
import com.lyhn.coreworkflowjava.workflow.engine.node.callback.WorkflowMsgCallback;
import com.lyhn.coreworkflowjava.workflow.engine.util.AsyncUtil;
import com.lyhn.coreworkflowjava.workflow.engine.util.FlowUtil;
import com.lyhn.coreworkflowjava.workflow.exception.ErrorCode;
import com.lyhn.coreworkflowjava.workflow.exception.NodeCustomException;
import jakarta.websocket.OnClose;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// 所有节点执行器的抽象基类
// AbstractNodeExecutor 是工作流节点执行器的抽象基类，实现了节点执行的通用逻辑，为所有类型的节点执行器提供了统一的执行框架，子类只需要实现具体的业务逻辑
@Slf4j
public abstract class AbstractNodeExecutor implements NodeExecutor {
    // 定义执行框架
    @Override
    public NodeRunResult execute(NodeState nodeState) throws Exception {
        Node node = nodeState.node();

        // 执行次数，用于支撑重试和限次控制
        int executeTime = node.getExecutedCount().addAndGet(1);
        RetryConfig retryConfig = node.getData().getRetryConfig();
        if(retryConfig == null){
            return this.doExecute(nodeState);
        }

        // 有配置就使用超时控制，无论是否重试
        if(!BooleanUtil.isTrue(retryConfig.getShouldRetry())){
            // 不支持重试，但支持超时控制
            return this.doExecuteWithTimeOut(nodeState,retryConfig);
        }

        // 支持重试 + 超时控制
        while(true){
            NodeRunResult res = this.doExecuteWithTimeOut(nodeState,retryConfig);
            NodeExecStatusEnum executeStatus = res.getStatus();
            if(executeStatus.isSuccess()){
                return res;
            }

            if(executeTime > retryConfig.getMaxRetries()){
                // 超过重试次数
                return res;
            }

            // 退避等待
            this.handleRetryWait(retryConfig, executeTime);
            executeTime = node.getExecutedCount().addAndGet(1);
        }
    }

    // 退避等待
    private void handleRetryWait(RetryConfig retryConfig, int retryCount) {
        if(retryConfig.getRetryInterval() == null || retryConfig.getRetryInterval() <= 0){
            return;
        }

        long intervalMillis = (long) (retryConfig.getRetryInterval() * 1000);
        long waitTime;

        Integer strategy = retryConfig.getRetryStrategy();
        if(strategy == null){
            strategy = 0;
        }

        switch (strategy) {
            case 0: // 固定间隔
                waitTime = intervalMillis;
                break;
            case 1: // 线性退避
                waitTime = intervalMillis * retryCount;
                break;
            case 2: // 指数退避
                waitTime = (long) (intervalMillis * Math.pow(2, retryCount - 1));
                break;
            default:
                waitTime = intervalMillis;
        }

        if (waitTime > 0) {
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                log.warn("Retry wait interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    // 将超时控制和重试机制结合在了一起，为所有节点执行器（如 LLMNodeExecutor ）提供了统一的执行框架
    // 节点执行的超时控制，负责在配置了超时时间的情况下，限制节点执行的最大等待时间
    protected NodeRunResult doExecuteWithTimeOut(NodeState nodeState, RetryConfig retryConfig) {
         // 设置了超时时间的场景
         if(retryConfig.timeOutEnabled()){
             try {
                 // 将节点的实际执行逻辑 () -> this.doExecute(nodeState) 封装成一个 Callable 对象
                 // 然后把它和从 retryConfig 中获取的超时时间一起，交给了 AsyncUtil
                 return AsyncUtil.callWithTimeLimit(retryConfig.toMillis(), TimeUnit.MILLISECONDS,
                         () -> this.doExecute(nodeState));
             } catch (TimeoutException | InterruptedException e) {
                 // 返回超时异常
                 NodeRunResult result = new NodeRunResult();
                 result.setError(new NodeCustomException(ErrorCode.TIMEOUT_ERROR));
                 return errorResponse(nodeState, result);
             } catch (Exception e) {
                 // 节点执行出现了非预期的异常
                 NodeRunResult result = new NodeRunResult();
                 result.setError(new NodeCustomException(ErrorCode.NODE_RUN_ERROR, e.getMessage()));
                 return errorResponse(nodeState, result);
             }
         } else {
             return this.doExecute(nodeState);
         }
    }

    // 节点执行的核心编排方法，定义了节点执行的标准流程，是 AbstractNodeExecutor 中最重要的模板方法实现
    protected NodeRunResult doExecute(NodeState nodeStage) {
        // 1:准备阶段，从 NodeState 中提取执行所需的上下文信息，node - 当前节点对象，callback - 工作流消息回调（用于通知前端），variablePool - 变量池（存储节点间传递的数据）
        // nodeId - 节点 ID（如 "node-llm::002"），nodeType - 节点类型（START/LLM/PLUGIN/END）
        Node node = nodeStage.node();
        WorkflowMsgCallback callback = nodeStage.callback();
        VariablePool variablePool = nodeStage.variablePool();
        String nodeId = node.getId();
        // 节点类型
        NodeTypeEnum nodeType = node.getNodeType();

        log.info("Executing node: {} (type: {})", nodeId, nodeType);

        // 2:通知阶段， 通知前端"节点开始执行"。
        // 开始执行节点
        callback.onNodeStart(0,nodeId,node.getData().getNodeMeta().getAliasName());

        // 3：输入阶段，起始节点直接从变量池获取，其他节点从节点配置和变量池中解析获取
        // 支持字面量值：{"topic": "AI技术"}，以及引用：{"script": "{{node-llm::002.llm_output}}"}
        // Resolve inputs
        Map<String, Object> resolvedInputs = node.getNodeType() == NodeTypeEnum.START ? variablePool.get(node.getId()) : resolveInputs(node, variablePool);
        try {
            // Execute node
            if (log.isDebugEnabled()) {
                log.debug("Executing start nodeId: {}, req: {}", node.getId(), JSON.toJSONString(resolvedInputs));
            }

            // 4：执行阶段，调用子类实现的具体业务逻辑，开始节点为 StartNodeExecutor、LLM 节点为 LLMNodeExecutor、插件节点为 PluginNodeExecutor、结束节点为 EndNodeExecutor
            NodeRunResult executeRes = executeNode(nodeStage, resolvedInputs);

            // 5：存储阶段，将节点的输出结果保存到变量池，供后续节点使用
            // Store outputs to variable pool
            /**
             *  LLM 节点执行
             *  ↓
             *  输出：{"llm_output": "这是生成的播客脚本..."}
             *  ↓
             *  storeOutputs() 保存
             *  ↓
             *  variablePool["node-llm::002"]["llm_output"] = "这是生成的播客脚本..."
             *  ↓
             *  TTS 节点引用
             *  ↓
             *  inputs: {"text": "{{node-llm::002.llm_output}}"}
             *  ↓
             *  resolveInputs() 解析
             *  ↓
             *  {"text": "这是生成的播客脚本..."}
             **/
            storeOutputs(node, executeRes.getOutputs(), variablePool);

            // 6：回调阶段，根据执行结果，通知前端节点执行完成了
            // Node 执行结束，结果回传
            if (executeRes.getStatus() == null || executeRes.getStatus().isSuccess()) {
                successResponse(nodeStage, executeRes);
            } else {
                errorResponse(nodeStage, executeRes);
            }
            return executeRes;
        } catch (NodeCustomException e) {
            log.error("NodeCustomException executing node {}: {}", nodeId, e.getMessage(), e);
            NodeRunResult result = new NodeRunResult();
            result.setInputs(resolvedInputs);
            result.setError(e);
            return errorResponse(nodeStage, result);
        } catch (Exception e) {
            log.error("Exception executing node {}: {}", nodeId, e.getMessage(), e);
            NodeRunResult result = new NodeRunResult();
            result.setInputs(resolvedInputs);
            result.setError(new NodeCustomException(ErrorCode.NODE_RUN_ERROR, e.getMessage()));
            return errorResponse(nodeStage, result);
        }
    }

    // 子类实现具体逻辑
    protected abstract NodeRunResult executeNode(NodeState nodeState, Map<String, Object> inputs) throws Exception;

    // 解析节点的输入参数 将引用的变量替换为实际值
    protected Map<String, Object> resolveInputs(Node node, VariablePool variablePool) {
        Map<String, Object> resolvedInputs = new HashMap<>();

        if (node.getData().getInputs() == null || node.getData().getInputs().isEmpty()) {
            log.debug("No inputs defined for node {}", node.getId());
            return resolvedInputs;
        }

        for (InputItem input : node.getData().getInputs()) {
            String inputName = input.getName();
            if (input.getSchema() == null || input.getSchema().getValue() == null) {
                log.warn("Input '{}' has no schema or value", inputName);
                continue;
            }

            Value value = input.getSchema().getValue();

            if(value.isReference()){
                // 引用模式 从其他节点获取输出
                if(value.getContent() instanceof java.util.Map){
                    @SuppressWarnings("unchecked")
                    Map<String, String> refMap = (Map<String, String>) value.getContent();
                    String refNodeId = refMap.get("nodeId");
                    String refName = refMap.get("name");

                    if (refNodeId != null && refName != null) {
                        // 说明 refName 可以是形如 xxx.yyy 的格式，其中 xxx 为 node的输出，yyy 为输出对象的某一个属性
                        Object refValue = variablePool.get(refNodeId, refName);
                        resolvedInputs.put(inputName, refValue);
                        if (log.isDebugEnabled()) {
                            log.debug("Resolved input '{}' from reference {}.{} = {}", inputName, refNodeId, refName, refValue);
                        }
                    }
                }else{
                    log.warn("Reference content is not a Map for input '{}'", inputName);
                }
            } else {
                // 字面量模式 直接将value作为输入参数
                resolvedInputs.put(inputName, value.getContent());
                if (log.isDebugEnabled()) {
                    log.debug("Resolved input '{}' from literal = {}", inputName, value.getContent());
                }
            }
        }
        return resolvedInputs;
    }

    // 将node节点的输出结果 保存到变量池 用于初始化其他node启动的输入参数
    protected void storeOutputs(Node node, Map<String, Object> outputs, VariablePool variablePool) {
        String nodeId = node.getId();

        for (Map.Entry<String, Object> entry : outputs.entrySet()) {
            String outputName = entry.getKey();
            Object outputValue = entry.getValue();
            if (outputValue == null) {
                continue;
            }
            variablePool.set(nodeId, outputName, outputValue);
            if (log.isDebugEnabled()) {
                log.debug("Stored output: {}.{} = {}", nodeId, outputName, outputValue);
            }
        }

    }

    private NodeRunResult getNodeRunResult(NodeState nodeState, RetryConfig retryConfig) {
        return this.doExecuteWithTimeOut(nodeState, retryConfig);
    }

    // 构建节点执行的成功状态
    protected void successResponse(NodeState nodeState, NodeRunResult result) {
        Node node = nodeState.node();
        WorkflowMsgCallback callback = nodeState.callback();
        switch (nodeState.node().getNodeType()) {
            case START ->
                    callback.onStartNodeExecuted(node.getId(), node.getData().getNodeMeta().getAliasName(), result);
            case END -> callback.onEndNodeExecuted(node.getId(), node.getData().getNodeMeta().getAliasName(), result);
            default -> callback.onNodeEnd(node.getId(), node.getData().getNodeMeta().getAliasName(), result);
        }
    }

    // 构建节点执行的失败状态
    private NodeRunResult errorResponse(NodeState nodeState, NodeRunResult result) {
        Node node = nodeState.node();
        RetryConfig retryConfig = node.getData().getRetryConfig();
        VariablePool variablePool = nodeState.variablePool();
        NodeCustomException e = result.getError();
        if (e == null) e = new NodeCustomException(ErrorCode.NODE_RUN_ERROR);
        WorkflowMsgCallback callback = nodeState.callback();
        log.warn("节点执行异常，进入异常分支流程: {}", node.getId(), e);
        if(retryConfig == null){
            // 直接进入中断流程
            result.setError(e);
            result.setStatus(NodeExecStatusEnum.ERR_INTERUPT);
            callback.onNodeInterrupt(FlowUtil.genInterruptEventId(), Map.of(), node.getId(), node.getData().getNodeMeta().getAliasName(), e.getCode(), "interrupt", false);
            return result;
        }

        ErrorStrategyEnum errorStrategy = ErrorStrategyEnum.fromCode(retryConfig.getErrorStrategy());
        Map<String, Object> customOutput = retryConfig.getCustomOutput();
        if (errorStrategy == ErrorStrategyEnum.ERR_CODE) {
            // 错误码的场景
            storeOutputs(node, customOutput, variablePool);
            result.setOutputs(customOutput);
            result.setError(e);
            result.setErrorOutputs(customOutput);
            result.setStatus(NodeExecStatusEnum.ERR_CODE_MSG);
            callback.onNodeEnd(node.getId(), node.getData().getNodeMeta().getAliasName(), result);
            return result;
        } else if (errorStrategy == ErrorStrategyEnum.ERR_CONDITION) {
            // 错误分支
            result.setError(e);
            result.setErrorOutputs(customOutput);
            result.setStatus(NodeExecStatusEnum.ERR_FAIL_CONDITION);
            callback.onNodeEnd(node.getId(), node.getData().getNodeMeta().getAliasName(), result);
            return result;
        } else {
            // 异常中断流程
            result.setError(e);
            result.setErrorOutputs(customOutput);
            result.setStatus(NodeExecStatusEnum.ERR_INTERUPT);
            callback.onNodeInterrupt(FlowUtil.genInterruptEventId(), customOutput, node.getId(), node.getData().getNodeMeta().getAliasName(), e.getCode(), "interrupt", false);
            return result;
        }
    }
}
