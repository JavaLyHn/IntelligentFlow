package com.lyhn.coreworkflowjava.workflow.engine.langgraph;

import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeExecStatusEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.domain.WorkflowDSL;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Edge;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Node;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.state.Channel;

import java.util.*;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

@Slf4j
public class GraphBuilder {

    private final NodeExecutorFactory executorFactory;
    private final Map<String, NodeExecutorAdapter> adapterMap = new LinkedHashMap<>();
    private final Map<String, Node> nodeMap = new LinkedHashMap<>();

    private Node startNode;
    private Node endNode;

    public GraphBuilder(NodeExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    public CompiledGraph<WorkflowAgentState> build(WorkflowDSL workflowDSL) throws GraphStateException {
        log.info("[GraphBuilder] Starting to build graph from DSL, nodes={}, edges={}",
                workflowDSL.getNodes().size(), workflowDSL.getEdges().size());

        validateDSL(workflowDSL);

        identifyEntryAndExit(workflowDSL);

        Map<String, Channel<?>> schema = WorkflowAgentState.SCHEMA;

        StateGraph<WorkflowAgentState> stateGraph = new StateGraph<>(
                schema,
                initData -> new WorkflowAgentState(initData)
        );

        registerNodes(stateGraph, workflowDSL);

        addEdges(stateGraph, workflowDSL);

        CompiledGraph<WorkflowAgentState> compiledGraph = stateGraph.compile();

        log.info("[GraphBuilder] Graph built successfully. Start node: {}, End node: {}, Adapters: {}",
                startNode.getId(), endNode.getId(), adapterMap.keySet());

        return compiledGraph;
    }

    private void validateDSL(WorkflowDSL workflowDSL) {
        if (workflowDSL.getNodes() == null || workflowDSL.getNodes().isEmpty()) {
            throw new IllegalArgumentException("Workflow DSL has no nodes");
        }
        if (workflowDSL.getEdges() == null || workflowDSL.getEdges().isEmpty()) {
            throw new IllegalArgumentException("Workflow DSL has no edges");
        }

        for (Node node : workflowDSL.getNodes()) {
            NodeTypeEnum nodeType = node.getNodeType();
            if (nodeType == null) {
                throw new IllegalArgumentException(
                        "Node has null type: " + node.getId());
            }
            if (nodeType != NodeTypeEnum.START && nodeType != NodeTypeEnum.END) {
                if (!executorFactory.supports(nodeType)) {
                    throw new IllegalArgumentException(
                            "No executor for node type: " + nodeType + " (node: " + node.getId() + ")");
                }
            }
        }
    }

    private void identifyEntryAndExit(WorkflowDSL workflowDSL) {
        for (Node node : workflowDSL.getNodes()) {
            nodeMap.put(node.getId(), node);
            if (node.getNodeType() == NodeTypeEnum.START) {
                if (startNode != null) {
                    throw new IllegalArgumentException("Multiple START nodes found");
                }
                startNode = node;
            }
            if (node.getNodeType() == NodeTypeEnum.END) {
                if (endNode != null) {
                    throw new IllegalArgumentException("Multiple END nodes found");
                }
                endNode = node;
            }
        }

        if (startNode == null) {
            throw new IllegalArgumentException("No START node found in workflow");
        }
        if (endNode == null) {
            throw new IllegalArgumentException("No END node found in workflow");
        }

        log.info("[GraphBuilder] Entry node identified: {} ({})",
                startNode.getId(), startNode.getNodeType());
        log.info("[GraphBuilder] Exit node identified: {} ({})",
                endNode.getId(), endNode.getNodeType());
    }

    private void registerNodes(StateGraph<WorkflowAgentState> stateGraph, WorkflowDSL workflowDSL) {
        for (Node node : workflowDSL.getNodes()) {
            String nodeId = node.getId();
            NodeTypeEnum nodeType = node.getNodeType();

            NodeExecutorAdapter adapter = new NodeExecutorAdapter(
                    executorFactory.getExecutor(nodeType), node);
            adapterMap.put(nodeId, adapter);

            try {
                stateGraph.addNode(nodeId, adapter);
                log.debug("[GraphBuilder] Registered node: {} (type: {})", nodeId, nodeType);
            } catch (GraphStateException e) {
                throw new RuntimeException(
                        "Failed to register node: " + nodeId, e);
            }
        }

        log.info("[GraphBuilder] Registered {} nodes", adapterMap.size());
    }

    private void addEdges(StateGraph<WorkflowAgentState> stateGraph, WorkflowDSL workflowDSL)
            throws GraphStateException {

        stateGraph.addEdge(START, startNode.getId());
        log.debug("[GraphBuilder] Added entry edge: START -> {}", startNode.getId());

        Map<String, List<Edge>> edgesBySource = workflowDSL.getEdges().stream()
                .collect(Collectors.groupingBy(Edge::getSource, LinkedHashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<Edge>> entry : edgesBySource.entrySet()) {
            String sourceId = entry.getKey();
            List<Edge> edges = entry.getValue();

            if (sourceId.equals(endNode.getId())) {
                stateGraph.addEdge(sourceId, END);
                log.debug("[GraphBuilder] Added exit edge: {} -> END", sourceId);
                continue;
            }

            addEdgesForSourceNode(stateGraph, sourceId, edges);
        }

        boolean endNodeHasOutgoingEdge = edgesBySource.containsKey(endNode.getId());
        if (!endNodeHasOutgoingEdge) {
            stateGraph.addEdge(endNode.getId(), END);
            log.debug("[GraphBuilder] Added default exit edge: {} -> END", endNode.getId());
        }
    }

    private void addEdgesForSourceNode(StateGraph<WorkflowAgentState> stateGraph,
                                        String sourceId, List<Edge> edges)
            throws GraphStateException {

        List<Edge> normalEdges = new ArrayList<>();
        List<Edge> failureEdges = new ArrayList<>();

        for (Edge edge : edges) {
            String handle = edge.getSourceHandle();
            if (StringUtils.isNotBlank(handle)
                    && handle.startsWith(NodeTypeEnum.CONDITION_SWITCH_INTENT_CHAIN.getValue())) {
                failureEdges.add(edge);
            } else {
                normalEdges.add(edge);
            }
        }

        boolean hasConditionalBranch = !failureEdges.isEmpty() && !normalEdges.isEmpty();
        boolean hasOnlyFailureBranch = !failureEdges.isEmpty() && normalEdges.isEmpty();

        if (hasConditionalBranch || hasOnlyFailureBranch) {
            addConditionalEdges(stateGraph, sourceId, normalEdges, failureEdges);
        } else {
            for (Edge edge : normalEdges) {
                String targetId = edge.getTarget();
                stateGraph.addEdge(sourceId, targetId);
                log.debug("[GraphBuilder] Added normal edge: {} -> {}", sourceId, targetId);
            }
        }
    }

    private void addConditionalEdges(StateGraph<WorkflowAgentState> stateGraph,
                                      String sourceId,
                                      List<Edge> normalEdges,
                                      List<Edge> failureEdges)
            throws GraphStateException {

        Map<String, String> routingMap = new LinkedHashMap<>();

        if (!normalEdges.isEmpty()) {
            String normalTarget = normalEdges.get(0).getTarget();
            routingMap.put("normal", normalTarget);

            for (int i = 1; i < normalEdges.size(); i++) {
                String parallelTarget = normalEdges.get(i).getTarget();
                routingMap.put("normal_" + i, parallelTarget);
            }
        }

        for (int i = 0; i < failureEdges.size(); i++) {
            String failureTarget = failureEdges.get(i).getTarget();
            String key = failureEdges.size() == 1 ? "failure" : "failure_" + i;
            routingMap.put(key, failureTarget);
        }

        routingMap.put("interrupt", END);

        final String capturedSourceId = sourceId;

        org.bsc.langgraph4j.action.EdgeAction<WorkflowAgentState> edgeAction = state -> {
            java.util.List<NodeResultEntry> results = state.getNodeResults();
            java.util.List<NodeResultEntry> sourceResults = results.stream()
                    .filter(r -> r.getNodeId().equals(capturedSourceId))
                    .collect(Collectors.toList());

            if (sourceResults.isEmpty()) {
                return "normal";
            }

            NodeResultEntry lastResult = sourceResults.get(sourceResults.size() - 1);
            NodeExecStatusEnum status = lastResult.getStatus();

            if (status == null || status.isSuccess()) {
                return "normal";
            }

            if (status == NodeExecStatusEnum.ERR_FAIL_CONDITION) {
                return failureEdges.isEmpty() ? "interrupt" : "failure";
            }

            if (status == NodeExecStatusEnum.ERR_CODE_MSG) {
                return "normal";
            }

            if (status == NodeExecStatusEnum.ERR_INTERUPT) {
                return "interrupt";
            }

            return "normal";
        };

        stateGraph.addConditionalEdges(sourceId, AsyncEdgeAction.edge_async(edgeAction), routingMap);

        log.debug("[GraphBuilder] Added conditional edges from {}: routing={}", sourceId, routingMap);
    }
}
