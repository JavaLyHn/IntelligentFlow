package com.lyhn.coreworkflowjava.workflow.engine.langgraph;

import com.lyhn.coreworkflowjava.workflow.engine.VariablePool;
import com.lyhn.coreworkflowjava.workflow.engine.constants.EndNodeOutputModeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeRunResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.WorkflowDSL;
import com.lyhn.coreworkflowjava.workflow.engine.domain.callbacks.ChatCallBackStreamResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.callbacks.LLMGenerate;
import com.lyhn.coreworkflowjava.workflow.engine.node.StreamCallback;
import com.lyhn.coreworkflowjava.workflow.engine.node.callback.WorkflowMsgCallback;
import com.lyhn.coreworkflowjava.workflow.engine.util.AsyncUtil;
import com.lyhn.coreworkflowjava.workflow.engine.util.FlowUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Component
public class LangGraphWorkflowEngine {

    private final NodeExecutorFactory executorFactory;

    public LangGraphWorkflowEngine(NodeExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    public void execute(WorkflowDSL workflowDSL, VariablePool variablePool,
                        Map<String, Object> inputs, StreamCallback callback) throws Exception {
        log.info("[LangGraphEngine] Starting workflow execution with {} nodes",
                workflowDSL.getNodes().size());

        variablePool.clear();

        Queue<ChatCallBackStreamResult> orderStreamResultQ = new ConcurrentLinkedQueue<>();
        Queue<LLMGenerate> streamQueue = new ConcurrentLinkedQueue<>();

        com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Node endNode =
                workflowDSL.getNodes().stream()
                        .filter(s -> s.getNodeType() == NodeTypeEnum.END)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No END node found"));

        String sid = FlowUtil.genWorkflowId(workflowDSL.getFlowId());
        WorkflowMsgCallback workflowCallback = new WorkflowMsgCallback(
                sid,
                callback,
                Objects.equals(endNode.getData().getNodeParam().get("outputMode"), 1)
                        ? EndNodeOutputModeEnum.VARIABLE_MODE
                        : EndNodeOutputModeEnum.DIRECT_MODE,
                streamQueue,
                orderStreamResultQ
        );

        workflowCallback.onWorkflowStart();

        try {
            GraphBuilder graphBuilder = new GraphBuilder(executorFactory);
            CompiledGraph<WorkflowAgentState> compiledGraph = graphBuilder.build(workflowDSL);

            Map<String, Object> initialState = buildInitialState(
                    workflowDSL, variablePool, inputs, workflowCallback);

            for (var item : compiledGraph.stream(initialState)) {
                log.debug("[LangGraphEngine] Stream item: {}", item);
            }

            log.info("[LangGraphEngine] Workflow {} execution completed successfully", sid);
            workflowCallback.onWorkflowEnd(new NodeRunResult());

        } catch (GraphStateException e) {
            log.error("[LangGraphEngine] Graph state error: {}", e.getMessage(), e);
            workflowCallback.onWorkflowEnd(new NodeRunResult());
            throw new RuntimeException("Workflow graph error: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("[LangGraphEngine] Workflow execution failed: {}", e.getMessage(), e);
            workflowCallback.onWorkflowEnd(new NodeRunResult());
            throw e;
        } finally {
            workflowCallback.finished();
        }
    }

    private Map<String, Object> buildInitialState(WorkflowDSL workflowDSL,
                                                   VariablePool variablePool,
                                                   Map<String, Object> inputs,
                                                   WorkflowMsgCallback callback) {
        Map<String, Object> state = new LinkedHashMap<>();

        Map<String, Map<String, Object>> poolData = extractVariablePoolData(variablePool);

        com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Node startNode =
                workflowDSL.getNodes().stream()
                        .filter(s -> s.getNodeType() == NodeTypeEnum.START)
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No START node found"));

        Map<String, Object> startOutputs = new LinkedHashMap<>();
        if (inputs != null) {
            startOutputs.putAll(inputs);
        }
        poolData.put(startNode.getId(), startOutputs);

        state.put(WorkflowAgentState.VARIABLE_POOL, poolData);
        state.put(WorkflowAgentState.NODE_RESULTS, new ArrayList<>());
        state.put(WorkflowAgentState.CALLBACK, callback);
        state.put(WorkflowAgentState.INPUTS, inputs != null ? inputs : new HashMap<>());
        state.put(WorkflowAgentState.ERROR, null);
        state.put(WorkflowAgentState.FLOW_ID, workflowDSL.getFlowId());
        state.put(WorkflowAgentState.CHAT_ID, workflowDSL.getUuid());

        return state;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> extractVariablePoolData(VariablePool variablePool) {
        Map<String, Map<String, Object>> data = new LinkedHashMap<>();
        try {
            java.lang.reflect.Field field = VariablePool.class.getDeclaredField("variables");
            field.setAccessible(true);
            Map<String, Map<String, Object>> internal =
                    (Map<String, Map<String, Object>>) field.get(variablePool);
            data.putAll(internal);
        } catch (Exception e) {
            log.warn("[LangGraphEngine] Failed to extract variable pool data: {}", e.getMessage());
        }
        return data;
    }
}
