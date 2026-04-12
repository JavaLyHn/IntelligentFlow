package com.lyhn.coreworkflowjava.workflow.engine.langgraph;

import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeExecStatusEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeRunResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeState;
import com.lyhn.coreworkflowjava.workflow.engine.domain.WorkflowDSL;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Edge;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Node;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.NodeData;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.NodeMeta;
import com.lyhn.coreworkflowjava.workflow.engine.node.NodeExecutor;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GraphBuilderTest {

    private NodeExecutorFactory executorFactory;

    @BeforeEach
    void setUp() {
        List<NodeExecutor> executors = Arrays.asList(
                new StubNodeExecutor(NodeTypeEnum.START),
                new StubNodeExecutor(NodeTypeEnum.END),
                new StubNodeExecutor(NodeTypeEnum.LLM),
                new StubNodeExecutor(NodeTypeEnum.PLUGIN),
                new StubNodeExecutor(NodeTypeEnum.IF_ELSE),
                new StubNodeExecutor(NodeTypeEnum.CODE),
                new StubNodeExecutor(NodeTypeEnum.MESSAGE)
        );
        executorFactory = new NodeExecutorFactory(executors);
    }

    @Test
    @DisplayName("线性工作流: START -> LLM -> END")
    void testLinearWorkflow() throws GraphStateException {
        WorkflowDSL dsl = new WorkflowDSL();
        dsl.setFlowId("test-flow-001");
        dsl.setUuid("test-uuid-001");

        Node startNode = createNode("node-start::001", NodeTypeEnum.START, "开始");
        Node llmNode = createNode("spark-llm::002", NodeTypeEnum.LLM, "大模型");
        Node endNode = createNode("node-end::003", NodeTypeEnum.END, "结束");

        dsl.setNodes(Arrays.asList(startNode, llmNode, endNode));
        dsl.setEdges(Arrays.asList(
                createEdge("node-start::001", "spark-llm::002", null),
                createEdge("spark-llm::002", "node-end::003", null)
        ));

        GraphBuilder builder = new GraphBuilder(executorFactory);
        CompiledGraph<WorkflowAgentState> graph = builder.build(dsl);

        assertNotNull(graph, "CompiledGraph should not be null");
    }

    @Test
    @DisplayName("条件分支工作流: START -> IF_ELSE -> (LLM | PLUGIN) -> END")
    void testConditionalWorkflow() throws GraphStateException {
        WorkflowDSL dsl = new WorkflowDSL();
        dsl.setFlowId("test-flow-002");
        dsl.setUuid("test-uuid-002");

        Node startNode = createNode("node-start::001", NodeTypeEnum.START, "开始");
        Node ifElseNode = createNode("if-else::002", NodeTypeEnum.IF_ELSE, "条件判断");
        Node llmNode = createNode("spark-llm::003", NodeTypeEnum.LLM, "大模型");
        Node pluginNode = createNode("plugin::004", NodeTypeEnum.PLUGIN, "插件");
        Node endNode = createNode("node-end::005", NodeTypeEnum.END, "结束");

        dsl.setNodes(Arrays.asList(startNode, ifElseNode, llmNode, pluginNode, endNode));
        dsl.setEdges(Arrays.asList(
                createEdge("node-start::001", "if-else::002", null),
                createEdge("if-else::002", "spark-llm::003", "normal_one_of::output_1"),
                createEdge("if-else::002", "plugin::004", "intent_chain::output_1"),
                createEdge("spark-llm::003", "node-end::005", null),
                createEdge("plugin::004", "node-end::005", null)
        ));

        GraphBuilder builder = new GraphBuilder(executorFactory);
        CompiledGraph<WorkflowAgentState> graph = builder.build(dsl);

        assertNotNull(graph, "CompiledGraph should not be null");
    }

    @Test
    @DisplayName("并行分支工作流: START -> (LLM + PLUGIN) -> END")
    void testParallelBranchWorkflow() throws GraphStateException {
        WorkflowDSL dsl = new WorkflowDSL();
        dsl.setFlowId("test-flow-003");
        dsl.setUuid("test-uuid-003");

        Node startNode = createNode("node-start::001", NodeTypeEnum.START, "开始");
        Node llmNode = createNode("spark-llm::002", NodeTypeEnum.LLM, "大模型");
        Node pluginNode = createNode("plugin::003", NodeTypeEnum.PLUGIN, "插件");
        Node endNode = createNode("node-end::004", NodeTypeEnum.END, "结束");

        dsl.setNodes(Arrays.asList(startNode, llmNode, pluginNode, endNode));
        dsl.setEdges(Arrays.asList(
                createEdge("node-start::001", "spark-llm::002", null),
                createEdge("node-start::001", "plugin::003", null),
                createEdge("spark-llm::002", "node-end::004", null),
                createEdge("plugin::003", "node-end::004", null)
        ));

        GraphBuilder builder = new GraphBuilder(executorFactory);
        CompiledGraph<WorkflowAgentState> graph = builder.build(dsl);

        assertNotNull(graph, "CompiledGraph should not be null");
    }

    @Test
    @DisplayName("DSL校验 - 无START节点应抛异常")
    void testValidateDSL_NoStartNode() {
        WorkflowDSL dsl = new WorkflowDSL();
        dsl.setFlowId("test-flow-004");

        Node llmNode = createNode("spark-llm::001", NodeTypeEnum.LLM, "大模型");
        Node endNode = createNode("node-end::002", NodeTypeEnum.END, "结束");

        dsl.setNodes(Arrays.asList(llmNode, endNode));
        dsl.setEdges(Arrays.asList(
                createEdge("spark-llm::001", "node-end::002", null)
        ));

        GraphBuilder builder = new GraphBuilder(executorFactory);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.build(dsl));
        assertTrue(ex.getMessage().contains("No START node"),
                "Should report missing START node");
    }

    @Test
    @DisplayName("DSL校验 - 无END节点应抛异常")
    void testValidateDSL_NoEndNode() {
        WorkflowDSL dsl = new WorkflowDSL();
        dsl.setFlowId("test-flow-005");

        Node startNode = createNode("node-start::001", NodeTypeEnum.START, "开始");
        Node llmNode = createNode("spark-llm::002", NodeTypeEnum.LLM, "大模型");

        dsl.setNodes(Arrays.asList(startNode, llmNode));
        dsl.setEdges(Arrays.asList(
                createEdge("node-start::001", "spark-llm::002", null)
        ));

        GraphBuilder builder = new GraphBuilder(executorFactory);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.build(dsl));
        assertTrue(ex.getMessage().contains("No END node"),
                "Should report missing END node");
    }

    @Test
    @DisplayName("DSL校验 - 不支持的节点类型应抛异常")
    void testValidateDSL_UnsupportedNodeType() {
        NodeExecutorFactory limitedFactory = new NodeExecutorFactory(
                Arrays.asList(
                        new StubNodeExecutor(NodeTypeEnum.START),
                        new StubNodeExecutor(NodeTypeEnum.END)
                )
        );

        WorkflowDSL dsl = new WorkflowDSL();
        dsl.setFlowId("test-flow-006");

        Node startNode = createNode("node-start::001", NodeTypeEnum.START, "开始");
        Node llmNode = createNode("spark-llm::002", NodeTypeEnum.LLM, "大模型");
        Node endNode = createNode("node-end::003", NodeTypeEnum.END, "结束");

        dsl.setNodes(Arrays.asList(startNode, llmNode, endNode));
        dsl.setEdges(Arrays.asList(
                createEdge("node-start::001", "spark-llm::002", null),
                createEdge("spark-llm::002", "node-end::003", null)
        ));

        GraphBuilder builder = new GraphBuilder(limitedFactory);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> builder.build(dsl));
        assertTrue(ex.getMessage().contains("No executor"),
                "Should report missing executor");
    }

    @Test
    @DisplayName("流式执行线性工作流并验证状态传递")
    void testStreamExecution() throws GraphStateException {
        WorkflowDSL dsl = new WorkflowDSL();
        dsl.setFlowId("test-flow-007");
        dsl.setUuid("test-uuid-007");

        Node startNode = createNode("node-start::001", NodeTypeEnum.START, "开始");
        Node llmNode = createNode("spark-llm::002", NodeTypeEnum.LLM, "大模型");
        Node endNode = createNode("node-end::003", NodeTypeEnum.END, "结束");

        dsl.setNodes(Arrays.asList(startNode, llmNode, endNode));
        dsl.setEdges(Arrays.asList(
                createEdge("node-start::001", "spark-llm::002", null),
                createEdge("spark-llm::002", "node-end::003", null)
        ));

        GraphBuilder builder = new GraphBuilder(executorFactory);
        CompiledGraph<WorkflowAgentState> graph = builder.build(dsl);

        Map<String, Object> initialState = new LinkedHashMap<>();
        initialState.put(WorkflowAgentState.VARIABLE_POOL, new HashMap<>());
        initialState.put(WorkflowAgentState.NODE_RESULTS, new ArrayList<>());
        initialState.put(WorkflowAgentState.INPUTS, Map.of("query", "你好"));
        initialState.put(WorkflowAgentState.FLOW_ID, "test-flow-007");
        initialState.put(WorkflowAgentState.CHAT_ID, "test-chat-007");
        initialState.put(WorkflowAgentState.ERROR, "");
        initialState.put(WorkflowAgentState.CALLBACK, "");

        List<String> executedNodes = new ArrayList<>();
        for (var item : graph.stream(initialState)) {
            String nodeName = item.node();
            executedNodes.add(nodeName);
        }

        assertTrue(executedNodes.contains("node-start::001"),
                "START node should be executed");
        assertTrue(executedNodes.contains("spark-llm::002"),
                "LLM node should be executed");
        assertTrue(executedNodes.contains("node-end::003"),
                "END node should be executed");
    }

    @Test
    @DisplayName("NodeExecutorFactory 注册和查询")
    void testNodeExecutorFactory() {
        assertTrue(executorFactory.supports(NodeTypeEnum.START));
        assertTrue(executorFactory.supports(NodeTypeEnum.END));
        assertTrue(executorFactory.supports(NodeTypeEnum.LLM));
        assertTrue(executorFactory.supports(NodeTypeEnum.PLUGIN));

        assertNotNull(executorFactory.getExecutor(NodeTypeEnum.LLM));
        assertThrows(IllegalArgumentException.class,
                () -> executorFactory.getExecutor(NodeTypeEnum.ITERATION));
    }

    @Test
    @DisplayName("NodeResultEntry 数据结构")
    void testNodeResultEntry() {
        NodeResultEntry entry = new NodeResultEntry(
                "spark-llm::001",
                "spark-llm",
                "大模型",
                NodeExecStatusEnum.SUCCESS,
                Map.of("text", "Hello"),
                null,
                150L
        );

        assertEquals("spark-llm::001", entry.getNodeId());
        assertEquals("spark-llm", entry.getNodeType());
        assertEquals("大模型", entry.getAliasName());
        assertEquals(NodeExecStatusEnum.SUCCESS, entry.getStatus());
        assertEquals("Hello", entry.getOutputs().get("text"));
        assertNull(entry.getErrorMessage());
        assertEquals(150L, entry.getExecutedTimeMs());
    }

    @Test
    @DisplayName("WorkflowAgentState 状态读写")
    void testWorkflowAgentState() {
        Map<String, Object> initData = new HashMap<>();
        initData.put(WorkflowAgentState.FLOW_ID, "flow-123");
        initData.put(WorkflowAgentState.CHAT_ID, "chat-456");
        initData.put(WorkflowAgentState.VARIABLE_POOL, new HashMap<>());
        initData.put(WorkflowAgentState.NODE_RESULTS, new ArrayList<>());

        WorkflowAgentState state = new WorkflowAgentState(initData);

        assertEquals("flow-123", state.getFlowId());
        assertEquals("chat-456", state.getChatId());
        assertNotNull(state.getVariablePoolData());
        assertNotNull(state.getNodeResults());
    }

    private Node createNode(String id, NodeTypeEnum type, String aliasName) {
        Node node = new Node();
        node.setId(id);

        NodeData data = new NodeData();
        NodeMeta meta = new NodeMeta();
        meta.setNodeType(type.getValue());
        meta.setAliasName(aliasName);
        data.setNodeMeta(meta);
        data.setNodeParam(new HashMap<>());
        node.setData(data);

        return node;
    }

    private Edge createEdge(String source, String target, String sourceHandle) {
        Edge edge = new Edge();
        edge.setSourceNodeId(source);
        edge.setTargetNodeId(target);
        edge.setSourceHandle(sourceHandle);
        return edge;
    }

    private static class StubNodeExecutor implements NodeExecutor {

        private final NodeTypeEnum nodeType;

        StubNodeExecutor(NodeTypeEnum nodeType) {
            this.nodeType = nodeType;
        }

        @Override
        public NodeRunResult execute(NodeState nodeState) throws Exception {
            NodeRunResult result = new NodeRunResult();
            result.setStatus(NodeExecStatusEnum.SUCCESS);

            if (nodeType == NodeTypeEnum.START) {
                result.getOutputs().put("query", "test-input");
            } else if (nodeType == NodeTypeEnum.LLM) {
                result.getOutputs().put("text", "LLM response");
                result.getOutputs().put("reasoning", "thinking...");
            } else if (nodeType == NodeTypeEnum.PLUGIN) {
                result.getOutputs().put("result", "plugin executed");
            } else if (nodeType == NodeTypeEnum.IF_ELSE) {
                result.getOutputs().put("branch", "normal");
            } else if (nodeType == NodeTypeEnum.CODE) {
                result.getOutputs().put("output", "code result");
            } else if (nodeType == NodeTypeEnum.MESSAGE) {
                result.getOutputs().put("message", "hello");
            }

            return result;
        }

        @Override
        public NodeTypeEnum getNodeType() {
            return nodeType;
        }
    }
}
