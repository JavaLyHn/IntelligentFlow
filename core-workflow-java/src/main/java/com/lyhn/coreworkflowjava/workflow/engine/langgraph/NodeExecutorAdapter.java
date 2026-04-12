package com.lyhn.coreworkflowjava.workflow.engine.langgraph;

import com.lyhn.coreworkflowjava.workflow.engine.VariablePool;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeExecStatusEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.context.EngineContextHolder;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeRunResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeState;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Node;
import com.lyhn.coreworkflowjava.workflow.engine.node.NodeExecutor;
import com.lyhn.coreworkflowjava.workflow.engine.node.callback.WorkflowMsgCallback;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class NodeExecutorAdapter implements AsyncNodeAction<WorkflowAgentState> {

    private final NodeExecutor nodeExecutor;
    private final Node node;

    public NodeExecutorAdapter(NodeExecutor nodeExecutor, Node node) {
        this.nodeExecutor = nodeExecutor;
        this.node = node;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(WorkflowAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String nodeId = node.getId();
            NodeTypeEnum nodeType = node.getNodeType();

            log.info("[LangGraph] Executing node: {} (type: {})", nodeId, nodeType);

            try {
                VariablePool variablePool = rebuildVariablePool(state);
                WorkflowMsgCallback callback = getCallback(state);

                node.init();
                NodeState nodeState = new NodeState(node, variablePool, callback);

                NodeRunResult result = nodeExecutor.execute(nodeState);

                Map<String, Map<String, Object>> updatedPoolData = extractUpdatedPoolData(variablePool, state);

                long executedTime = System.currentTimeMillis() - startTime;

                NodeResultEntry entry = new NodeResultEntry(
                        nodeId,
                        nodeType != null ? nodeType.getValue() : "",
                        node.getData() != null && node.getData().getNodeMeta() != null
                                ? node.getData().getNodeMeta().getAliasName() : "",
                        result.getStatus() != null ? result.getStatus() : NodeExecStatusEnum.SUCCESS,
                        result.getOutputs(),
                        result.getError() != null ? result.getError().getMessage() : null,
                        executedTime
                );

                Map<String, Object> updates = new LinkedHashMap<>();
                updates.put(WorkflowAgentState.VARIABLE_POOL, updatedPoolData);

                java.util.List<NodeResultEntry> results = new java.util.ArrayList<>();
                results.add(entry);
                updates.put(WorkflowAgentState.NODE_RESULTS, results);

                if (result.getError() != null) {
                    updates.put(WorkflowAgentState.ERROR, result.getError().getMessage());
                }

                log.info("[LangGraph] Node {} completed in {}ms, status={}", nodeId, executedTime,
                        result.getStatus());

                return updates;

            } catch (Exception e) {
                log.error("[LangGraph] Node {} execution failed: {}", nodeId, e.getMessage(), e);
                long executedTime = System.currentTimeMillis() - startTime;

                NodeResultEntry entry = new NodeResultEntry(
                        nodeId,
                        nodeType != null ? nodeType.getValue() : "",
                        node.getData() != null && node.getData().getNodeMeta() != null
                                ? node.getData().getNodeMeta().getAliasName() : "",
                        NodeExecStatusEnum.ERR_INTERUPT,
                        new HashMap<>(),
                        e.getMessage(),
                        executedTime
                );

                Map<String, Object> updates = new LinkedHashMap<>();
                java.util.List<NodeResultEntry> results = new java.util.ArrayList<>();
                results.add(entry);
                updates.put(WorkflowAgentState.NODE_RESULTS, results);
                updates.put(WorkflowAgentState.ERROR, e.getMessage());

                return updates;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private VariablePool rebuildVariablePool(WorkflowAgentState state) {
        VariablePool pool = new VariablePool();
        Map<String, Map<String, Object>> poolData = state.getVariablePoolData();
        for (Map.Entry<String, Map<String, Object>> nodeEntry : poolData.entrySet()) {
            String poolNodeId = nodeEntry.getKey();
            Map<String, Object> outputs = nodeEntry.getValue();
            if (outputs != null) {
                for (Map.Entry<String, Object> outputEntry : outputs.entrySet()) {
                    pool.set(poolNodeId, outputEntry.getKey(), outputEntry.getValue());
                }
            }
        }
        return pool;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> extractUpdatedPoolData(VariablePool variablePool, WorkflowAgentState state) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>(state.getVariablePoolData());

        try {
            java.lang.reflect.Field field = VariablePool.class.getDeclaredField("variables");
            field.setAccessible(true);
            Map<String, Map<String, Object>> internalVars =
                    (Map<String, Map<String, Object>>) field.get(variablePool);
            for (Map.Entry<String, Map<String, Object>> entry : internalVars.entrySet()) {
                Map<String, Object> existing = merged.getOrDefault(entry.getKey(), new LinkedHashMap<>());
                Map<String, Object> mergedOutputs = new LinkedHashMap<>(existing);
                if (entry.getValue() != null) {
                    mergedOutputs.putAll(entry.getValue());
                }
                merged.put(entry.getKey(), mergedOutputs);
            }
        } catch (Exception e) {
            log.warn("[LangGraph] Failed to extract variable pool data: {}", e.getMessage());
        }

        return merged;
    }

    private WorkflowMsgCallback getCallback(WorkflowAgentState state) {
        EngineContextHolder.EngineContext ctx = EngineContextHolder.get();
        if (ctx != null && ctx.getCallback() != null) {
            return ctx.getCallback();
        }
        return null;
    }

    public String getNodeId() {
        return node.getId();
    }

    public NodeTypeEnum getNodeType() {
        return node.getNodeType();
    }
}
