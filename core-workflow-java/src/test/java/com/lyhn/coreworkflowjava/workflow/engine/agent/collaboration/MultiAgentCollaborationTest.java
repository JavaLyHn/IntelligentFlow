package com.lyhn.coreworkflowjava.workflow.engine.agent.collaboration;

import com.lyhn.coreworkflowjava.workflow.engine.agent.research.SearchResult;
import com.lyhn.coreworkflowjava.workflow.engine.agent.research.SynthesizeExecutor;
import com.lyhn.coreworkflowjava.workflow.engine.agent.research.SearchExecutor;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.node.impl.collaboration.MultiAgentNodeExecutor;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MultiAgentCollaborationTest {

    private SearchExecutor searchExecutor;
    private SynthesizeExecutor synthesizeExecutor;

    @BeforeEach
    void setUp() {
        searchExecutor = query -> List.of(
                SearchResult.builder()
                        .title("Result for: " + query)
                        .snippet("Information about " + query)
                        .source("test")
                        .relevanceScore(0.9)
                        .build()
        );

        synthesizeExecutor = (query, observations, history) ->
                "# Report: " + query + "\n\n" + observations;
    }

    @Nested
    @DisplayName("AgentRole 测试")
    class AgentRoleTest {

        @Test
        @DisplayName("AgentRole - fromValue 正确解析")
        void testFromValue() {
            assertEquals(AgentRole.SUPERVISOR, AgentRole.fromValue("supervisor"));
            assertEquals(AgentRole.SEARCHER, AgentRole.fromValue("searcher"));
            assertEquals(AgentRole.ANALYZER, AgentRole.fromValue("analyzer"));
            assertEquals(AgentRole.WRITER, AgentRole.fromValue("writer"));
            assertEquals(AgentRole.JUDGE, AgentRole.fromValue("judge"));
        }

        @Test
        @DisplayName("AgentRole - 无效值默认返回 SEARCHER")
        void testFromValueInvalid() {
            assertEquals(AgentRole.SEARCHER, AgentRole.fromValue("invalid"));
            assertEquals(AgentRole.SEARCHER, AgentRole.fromValue(""));
            assertEquals(AgentRole.SEARCHER, AgentRole.fromValue(null));
        }
    }

    @Nested
    @DisplayName("AgentDefinition 测试")
    class AgentDefinitionTest {

        @Test
        @DisplayName("AgentDefinition - 工厂方法创建")
        void testFactoryMethods() {
            AgentDefinition supervisor = AgentDefinition.supervisor("主管", "你是一个主管");
            assertEquals(AgentRole.SUPERVISOR, supervisor.getRole());
            assertTrue(supervisor.hasCapability("task_decomposition"));

            AgentDefinition searcher = AgentDefinition.searcher("搜索员", "你是一个搜索员");
            assertEquals(AgentRole.SEARCHER, searcher.getRole());
            assertTrue(searcher.hasCapability("web_search"));

            AgentDefinition judge = AgentDefinition.judge("评判员", "你是一个评判员");
            assertEquals(AgentRole.JUDGE, judge.getRole());
            assertTrue(judge.hasCapability("evaluation"));
        }

        @Test
        @DisplayName("AgentDefinition - 能力检查")
        void testHasCapability() {
            AgentDefinition agent = AgentDefinition.searcher("test", "prompt");
            assertTrue(agent.hasCapability("web_search"));
            assertFalse(agent.hasCapability("task_decomposition"));
        }
    }

    @Nested
    @DisplayName("HandoffProtocol 测试")
    class HandoffProtocolTest {

        @Test
        @DisplayName("HandoffProtocol - 委派交接")
        void testDelegate() {
            HandoffProtocol handoff = HandoffProtocol.delegate(
                    "supervisor", "searcher", "搜索AI相关资料", Map.of("priority", "high"));

            assertEquals("supervisor", handoff.getFromAgent());
            assertEquals("searcher", handoff.getToAgent());
            assertEquals(HandoffProtocol.HandoffType.DELEGATE, handoff.getType());
            assertEquals("high", handoff.getContext().get("priority"));
        }

        @Test
        @DisplayName("HandoffProtocol - 返回交接")
        void testReturnResult() {
            HandoffProtocol handoff = HandoffProtocol.returnResult(
                    "searcher", "supervisor", "搜索完成", null);

            assertEquals(HandoffProtocol.HandoffType.RETURN, handoff.getType());
        }

        @Test
        @DisplayName("HandoffProtocol - 汇聚交接")
        void testAggregate() {
            HandoffProtocol handoff = HandoffProtocol.aggregate(
                    "judge", "综合评判", List.of("worker-1", "worker-2"), null);

            assertEquals(HandoffProtocol.HandoffType.AGGREGATE, handoff.getType());
            assertTrue(handoff.getFromAgent().contains("worker-1"));
            assertTrue(handoff.getFromAgent().contains("worker-2"));
        }
    }

    @Nested
    @DisplayName("MultiAgentResult 测试")
    class MultiAgentResultTest {

        @Test
        @DisplayName("MultiAgentResult - 成功工厂方法")
        void testSuccessFactory() {
            MultiAgentResult result = MultiAgentResult.success(
                    "task", "supervisor", "output", Map.of("a", "b"), 5, 1000);

            assertTrue(result.isSuccess());
            assertEquals("task", result.getTask());
            assertEquals("supervisor", result.getMode());
            assertEquals(5, result.getTotalIterations());
        }

        @Test
        @DisplayName("MultiAgentResult - 失败工厂方法")
        void testFailureFactory() {
            MultiAgentResult result = MultiAgentResult.failure("task", "pipeline", "error", 500);

            assertFalse(result.isSuccess());
            assertEquals("error", result.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("CollaborationGraph 构建测试")
    class CollaborationGraphTest {

        @Test
        @DisplayName("Pipeline 模式 - 图构建成功")
        void testPipelineGraphBuild() throws Exception {
            CollaborationGraph graph = new CollaborationGraph(searchExecutor, synthesizeExecutor);

            List<AgentDefinition> agents = List.of(
                    AgentDefinition.searcher("搜索员", "搜索"),
                    AgentDefinition.analyzer("分析员", "分析"),
                    AgentDefinition.writer("写作员", "写作")
            );

            var compiledGraph = graph.buildPipelineGraph(agents, "test task", 10);
            assertNotNull(compiledGraph);
        }

        @Test
        @DisplayName("Pipeline 模式 - 至少需要2个Agent")
        void testPipelineRequiresTwoAgents() {
            CollaborationGraph graph = new CollaborationGraph(searchExecutor, synthesizeExecutor);

            List<AgentDefinition> agents = List.of(
                    AgentDefinition.searcher("搜索员", "搜索")
            );

            assertThrows(IllegalArgumentException.class,
                    () -> graph.buildPipelineGraph(agents, "test", 10));
        }

        @Test
        @DisplayName("Supervisor 模式 - 需要 SUPERVISOR 角色")
        void testSupervisorRequiresSupervisorRole() {
            CollaborationGraph graph = new CollaborationGraph(searchExecutor, synthesizeExecutor);

            List<AgentDefinition> agents = List.of(
                    AgentDefinition.searcher("搜索员", "搜索")
            );

            assertThrows(IllegalArgumentException.class,
                    () -> graph.buildSupervisorGraph(agents, "test", 10));
        }

        @Test
        @DisplayName("Swarm 模式 - 需要 JUDGE 角色")
        void testSwarmRequiresJudgeRole() {
            CollaborationGraph graph = new CollaborationGraph(searchExecutor, synthesizeExecutor);

            List<AgentDefinition> agents = List.of(
                    AgentDefinition.searcher("搜索员", "搜索")
            );

            assertThrows(IllegalArgumentException.class,
                    () -> graph.buildSwarmGraph(agents, "test", 10));
        }

        @Test
        @DisplayName("buildGraph - 不支持的模式抛异常")
        void testUnsupportedMode() {
            CollaborationGraph graph = new CollaborationGraph(searchExecutor, synthesizeExecutor);

            assertThrows(IllegalArgumentException.class,
                    () -> graph.buildGraph("invalid", List.of(), "test", 10));
        }
    }

    @Nested
    @DisplayName("MultiAgentOrchestrator 端到端测试")
    class OrchestratorEndToEndTest {

        @Test
        @DisplayName("Pipeline 模式 - 完整执行")
        void testPipelineExecution() {
            MultiAgentOrchestrator orchestrator = new MultiAgentOrchestrator(
                    searchExecutor, synthesizeExecutor);

            List<AgentDefinition> agents = List.of(
                    AgentDefinition.searcher("搜索员", "负责搜索"),
                    AgentDefinition.analyzer("分析员", "负责分析"),
                    AgentDefinition.writer("写作员", "负责写作")
            );

            MultiAgentResult result = orchestrator.orchestrate("pipeline", agents, "AI技术趋势", 10);

            assertTrue(result.isSuccess());
            assertNotNull(result.getFinalOutput());
            assertTrue(result.getFinalOutput().length() > 0);
            assertEquals("pipeline", result.getMode());
            assertTrue(result.getElapsedMs() > 0);
        }

        @Test
        @DisplayName("Supervisor 模式 - 完整执行")
        void testSupervisorExecution() {
            MultiAgentOrchestrator orchestrator = new MultiAgentOrchestrator(
                    searchExecutor, synthesizeExecutor);

            List<AgentDefinition> agents = List.of(
                    AgentDefinition.supervisor("研究主管", "负责拆解任务"),
                    AgentDefinition.searcher("搜索专家", "负责搜索"),
                    AgentDefinition.analyzer("分析专家", "负责分析"),
                    AgentDefinition.writer("写作专家", "负责写作")
            );

            MultiAgentResult result = orchestrator.orchestrate("supervisor", agents, "深度学习研究", 10);

            assertTrue(result.isSuccess());
            assertNotNull(result.getFinalOutput());
            assertTrue(result.getFinalOutput().length() > 0);
            assertEquals("supervisor", result.getMode());
        }

        @Test
        @DisplayName("Swarm 模式 - 完整执行")
        void testSwarmExecution() {
            MultiAgentOrchestrator orchestrator = new MultiAgentOrchestrator(
                    searchExecutor, synthesizeExecutor);

            List<AgentDefinition> agents = List.of(
                    AgentDefinition.searcher("搜索员A", "负责方向A搜索"),
                    AgentDefinition.searcher("搜索员B", "负责方向B搜索"),
                    AgentDefinition.judge("评判员", "负责综合评判")
            );

            MultiAgentResult result = orchestrator.orchestrate("swarm", agents, "AI安全", 10);

            assertTrue(result.isSuccess());
            assertNotNull(result.getFinalOutput());
            assertTrue(result.getFinalOutput().length() > 0);
            assertEquals("swarm", result.getMode());
        }

        @Test
        @DisplayName("三种模式都能产生结果")
        void testAllModesProduceResults() {
            MultiAgentOrchestrator orchestrator = new MultiAgentOrchestrator(
                    searchExecutor, synthesizeExecutor);

            String task = "量子计算研究";

            MultiAgentResult pipelineResult = orchestrator.orchestrate("pipeline",
                    List.of(AgentDefinition.searcher("s1", "搜索"),
                            AgentDefinition.writer("w1", "写作")),
                    task, 10);
            assertTrue(pipelineResult.isSuccess());

            MultiAgentResult supervisorResult = orchestrator.orchestrate("supervisor",
                    List.of(AgentDefinition.supervisor("sv", "主管"),
                            AgentDefinition.searcher("s2", "搜索"),
                            AgentDefinition.writer("w2", "写作")),
                    task, 10);
            assertTrue(supervisorResult.isSuccess());

            MultiAgentResult swarmResult = orchestrator.orchestrate("swarm",
                    List.of(AgentDefinition.searcher("s3", "搜索"),
                            AgentDefinition.judge("j1", "评判")),
                    task, 10);
            assertTrue(swarmResult.isSuccess());
        }
    }

    @Nested
    @DisplayName("NodeTypeEnum.MULTI_AGENT 测试")
    class NodeTypeEnumTest {

        @Test
        @DisplayName("MULTI_AGENT 类型已注册")
        void testMultiAgentTypeRegistered() {
            assertNotNull(NodeTypeEnum.MULTI_AGENT);
            assertEquals("multi-agent", NodeTypeEnum.MULTI_AGENT.getValue());
        }

        @Test
        @DisplayName("fromValue 能正确解析 MULTI_AGENT")
        void testFromValue() {
            assertEquals(NodeTypeEnum.MULTI_AGENT, NodeTypeEnum.fromValue("multi-agent"));
        }
    }

    @Nested
    @DisplayName("MultiAgentNodeExecutor 测试")
    class NodeExecutorTest {

        @Test
        @DisplayName("MultiAgentNodeExecutor - 节点类型正确")
        void testNodeType() {
            MultiAgentNodeExecutor executor = new MultiAgentNodeExecutor();
            assertEquals(NodeTypeEnum.MULTI_AGENT, executor.getNodeType());
        }
    }

    @Nested
    @DisplayName("AgentNodeAction 角色执行测试")
    class AgentNodeActionTest {

        @Test
        @DisplayName("Searcher 角色调用 SearchExecutor")
        void testSearcherRole() {
            AgentDefinition searcher = AgentDefinition.searcher("搜索员", "搜索");
            AgentNodeAction action = new AgentNodeAction(searcher, searchExecutor, synthesizeExecutor);

            Map<String, Object> initData = new HashMap<>();
            initData.put(CollaborationState.TASK, "AI研究");
            initData.put(CollaborationState.CURRENT_AGENT, "");
            initData.put(CollaborationState.ITERATION, 0);
            initData.put(CollaborationState.MAX_ITERATIONS, 10);
            initData.put(CollaborationState.AGENT_RESULTS, new HashMap<String, Object>());
            initData.put(CollaborationState.CONTEXT, new HashMap<String, Object>());
            initData.put(CollaborationState.FINAL_OUTPUT, "");

            CollaborationState state = new CollaborationState(initData);
            Map<String, Object> result = action.apply(state).join();

            assertNotNull(result);
            assertTrue(result.containsKey(CollaborationState.AGENT_RESULTS));
        }

        @Test
        @DisplayName("Writer 角色设置 FINAL_OUTPUT")
        void testWriterRoleSetsFinalOutput() {
            AgentDefinition writer = AgentDefinition.writer("写作员", "写作");
            AgentNodeAction action = new AgentNodeAction(writer, searchExecutor, synthesizeExecutor);

            Map<String, Object> initData = new HashMap<>();
            initData.put(CollaborationState.TASK, "AI研究");
            initData.put(CollaborationState.CURRENT_AGENT, "");
            initData.put(CollaborationState.ITERATION, 0);
            initData.put(CollaborationState.MAX_ITERATIONS, 10);
            initData.put(CollaborationState.AGENT_RESULTS, new HashMap<String, Object>());
            initData.put(CollaborationState.CONTEXT, new HashMap<String, Object>());
            initData.put(CollaborationState.FINAL_OUTPUT, "");

            CollaborationState state = new CollaborationState(initData);
            Map<String, Object> result = action.apply(state).join();

            assertTrue(result.containsKey(CollaborationState.FINAL_OUTPUT));
            String finalOutput = String.valueOf(result.get(CollaborationState.FINAL_OUTPUT));
            assertTrue(finalOutput.length() > 0);
        }
    }
}
