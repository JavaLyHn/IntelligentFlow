package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DeepResearchAgentTest {

    @Nested
    @DisplayName("ResearchState 测试")
    class ResearchStateTest {

        @Test
        @DisplayName("ResearchState - 初始状态")
        void testInitialState() {
            ResearchState state = ResearchState.builder()
                    .query("test query")
                    .build();

            assertEquals("test query", state.getQuery());
            assertEquals(ResearchState.ReActPhase.INIT, state.getPhase());
            assertEquals(0, state.getIterationCount());
            assertEquals(10, state.getMaxIterations());
            assertTrue(state.isShouldContinue());
            assertTrue(state.getCycles().isEmpty());
            assertTrue(state.getSearchResults().isEmpty());
        }

        @Test
        @DisplayName("ResearchState - 添加 ReAct 循环")
        void testAddCycle() {
            ResearchState state = ResearchState.builder().query("test").build();

            state.addCycle(new ReActCycle("thought1", "action1", "observation1"));
            state.addCycle(new ReActCycle("thought2", "action2", "observation2"));

            assertEquals(2, state.getIterationCount());
            assertEquals(2, state.getCycles().size());
        }

        @Test
        @DisplayName("ResearchState - 添加搜索结果")
        void testAddSearchResult() {
            ResearchState state = ResearchState.builder().query("test").build();

            state.addSearchResult(SearchResult.builder()
                    .title("Result 1").snippet("Snippet 1").source("source1").build());
            state.addSearchResult(SearchResult.builder()
                    .title("Result 2").snippet("Snippet 2").source("source2").build());

            assertEquals(2, state.getSearchResults().size());
        }

        @Test
        @DisplayName("ResearchState - 超过最大迭代次数")
        void testExceededMaxIterations() {
            ResearchState state = ResearchState.builder()
                    .query("test")
                    .maxIterations(3)
                    .build();

            assertFalse(state.hasExceededMaxIterations());

            state.addCycle(new ReActCycle("t1", "a1", "o1"));
            state.addCycle(new ReActCycle("t2", "a2", "o2"));
            state.addCycle(new ReActCycle("t3", "a3", "o3"));

            assertTrue(state.hasExceededMaxIterations());
        }

        @Test
        @DisplayName("ResearchState - 累积观察结果")
        void testAccumulatedObservations() {
            ResearchState state = ResearchState.builder().query("test").build();

            state.addSearchResult(SearchResult.builder()
                    .title("AI Overview").snippet("AI is transforming industries").source("wiki").build());
            state.addSearchResult(SearchResult.builder()
                    .title("AI Future").snippet("AI will continue to evolve").source("news").build());

            String observations = state.getAccumulatedObservations();
            assertTrue(observations.contains("AI Overview"));
            assertTrue(observations.contains("AI Future"));
        }

        @Test
        @DisplayName("ResearchState - 循环历史")
        void testCycleHistory() {
            ResearchState state = ResearchState.builder().query("test").build();

            state.addCycle(new ReActCycle("What is AI?", "search AI", "AI is..."));
            state.addCycle(new ReActCycle("How does AI work?", "search AI mechanism", "AI works by..."));

            String history = state.getCycleHistory();
            assertTrue(history.contains("What is AI?"));
            assertTrue(history.contains("search AI mechanism"));
        }

        @Test
        @DisplayName("ResearchState - 空观察结果和历史")
        void testEmptyObservationsAndHistory() {
            ResearchState state = ResearchState.builder().query("test").build();

            assertEquals("", state.getAccumulatedObservations());
            assertEquals("", state.getCycleHistory());
        }
    }

    @Nested
    @DisplayName("ResearchPlan 测试")
    class ResearchPlanTest {

        @Test
        @DisplayName("ResearchPlan - 创建计划")
        void testCreatePlan() {
            List<ResearchStep> steps = List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 1").searchQuery("query1").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 2").searchQuery("query2").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SYNTHESIZE)
                            .description("Synthesize").build()
            );

            ResearchPlan plan = ResearchPlan.builder()
                    .query("test query")
                    .steps(steps)
                    .build();

            assertEquals("test query", plan.getQuery());
            assertEquals(3, plan.getStepCount());
            assertEquals(0, plan.getCurrentStepIndex());
            assertEquals(ResearchPlan.PlanStatus.PENDING, plan.getStatus());
        }

        @Test
        @DisplayName("ResearchPlan - 获取当前步骤")
        void testGetCurrentStep() {
            List<ResearchStep> steps = List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 1").searchQuery("query1").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 2").searchQuery("query2").build()
            );

            ResearchPlan plan = ResearchPlan.builder().query("test").steps(steps).build();

            assertEquals("Step 1", plan.getCurrentStep().getDescription());
        }

        @Test
        @DisplayName("ResearchPlan - 推进到下一步")
        void testAdvanceToNextStep() {
            List<ResearchStep> steps = List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 1").searchQuery("query1").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 2").searchQuery("query2").build()
            );

            ResearchPlan plan = ResearchPlan.builder().query("test").steps(steps).build();

            plan.advanceToNextStep();
            assertEquals(1, plan.getCurrentStepIndex());
            assertEquals("Step 2", plan.getCurrentStep().getDescription());
        }

        @Test
        @DisplayName("ResearchPlan - 计划完成")
        void testPlanComplete() {
            List<ResearchStep> steps = List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 1").searchQuery("query1").build()
            );

            ResearchPlan plan = ResearchPlan.builder().query("test").steps(steps).build();

            assertFalse(plan.isComplete());
            plan.advanceToNextStep();
            assertTrue(plan.isComplete());
            assertEquals(ResearchPlan.PlanStatus.COMPLETED, plan.getStatus());
        }

        @Test
        @DisplayName("ResearchPlan - 空步骤列表")
        void testEmptySteps() {
            ResearchPlan plan = ResearchPlan.builder().query("test").steps(new ArrayList<>()).build();

            assertEquals(0, plan.getStepCount());
            assertNull(plan.getCurrentStep());
            assertTrue(plan.isComplete());
        }

        @Test
        @DisplayName("ResearchPlan - 默认值验证")
        void testDefaultValues() {
            ResearchPlan plan = ResearchPlan.builder().build();

            assertEquals(0, plan.getCurrentStepIndex());
            assertEquals(ResearchPlan.PlanStatus.PENDING, plan.getStatus());
            assertEquals(0, plan.getStepCount());
            assertEquals(0, plan.getCompletedStepCount());
        }
    }

    @Nested
    @DisplayName("ResearchStep 测试")
    class ResearchStepTest {

        @Test
        @DisplayName("ResearchStep - 状态转换")
        void testStatusTransitions() {
            ResearchStep step = ResearchStep.builder()
                    .type(ResearchStep.StepType.SEARCH)
                    .description("Test step")
                    .searchQuery("test query")
                    .build();

            assertEquals(ResearchStep.StepStatus.PENDING, step.getStatus());

            step.markInProgress();
            assertEquals(ResearchStep.StepStatus.IN_PROGRESS, step.getStatus());

            step.markCompleted("Result found");
            assertEquals(ResearchStep.StepStatus.COMPLETED, step.getStatus());
            assertEquals("Result found", step.getResult());
        }

        @Test
        @DisplayName("ResearchStep - 标记失败")
        void testMarkFailed() {
            ResearchStep step = ResearchStep.builder()
                    .type(ResearchStep.StepType.SEARCH)
                    .description("Test step")
                    .build();

            step.markFailed("Connection timeout");
            assertEquals(ResearchStep.StepStatus.FAILED, step.getStatus());
            assertEquals("Connection timeout", step.getResult());
        }

        @Test
        @DisplayName("ResearchStep - 类型枚举")
        void testStepTypes() {
            assertEquals(3, ResearchStep.StepType.values().length);
            assertNotNull(ResearchStep.StepType.valueOf("PLAN"));
            assertNotNull(ResearchStep.StepType.valueOf("SEARCH"));
            assertNotNull(ResearchStep.StepType.valueOf("SYNTHESIZE"));
        }

        @Test
        @DisplayName("ResearchStep - 状态枚举")
        void testStepStatuses() {
            assertEquals(5, ResearchStep.StepStatus.values().length);
        }
    }

    @Nested
    @DisplayName("SearchResult 测试")
    class SearchResultTest {

        @Test
        @DisplayName("SearchResult - 创建搜索结果")
        void testCreateSearchResult() {
            SearchResult result = SearchResult.builder()
                    .title("AI Research")
                    .url("https://example.com/ai")
                    .snippet("Latest AI research findings")
                    .source("academic")
                    .relevanceScore(0.95)
                    .build();

            assertEquals("AI Research", result.getTitle());
            assertEquals("https://example.com/ai", result.getUrl());
            assertEquals("Latest AI research findings", result.getSnippet());
            assertEquals(0.95, result.getRelevanceScore(), 0.001);
        }

        @Test
        @DisplayName("SearchResult - 默认相关度分数")
        void testDefaultRelevanceScore() {
            SearchResult result = SearchResult.builder()
                    .title("Test")
                    .snippet("Test snippet")
                    .build();

            assertEquals(0.0, result.getRelevanceScore(), 0.001);
        }
    }

    @Nested
    @DisplayName("ResearchResult 测试")
    class ResearchResultTest {

        @Test
        @DisplayName("ResearchResult - 成功结果工厂方法")
        void testSuccessFactory() {
            List<SearchResult> sources = List.of(
                    SearchResult.builder().title("Source 1").snippet("S1").build()
            );

            ResearchResult result = ResearchResult.success("query", "report", sources, 3, 1000);

            assertTrue(result.isSuccess());
            assertEquals("query", result.getQuery());
            assertEquals("report", result.getReport());
            assertEquals(1, result.getSources().size());
            assertEquals(3, result.getTotalIterations());
            assertEquals(1000, result.getElapsedMs());
            assertNull(result.getErrorMessage());
        }

        @Test
        @DisplayName("ResearchResult - 失败结果工厂方法")
        void testFailureFactory() {
            ResearchResult result = ResearchResult.failure("query", "timeout", 2, 5000);

            assertFalse(result.isSuccess());
            assertEquals("query", result.getQuery());
            assertEquals("timeout", result.getErrorMessage());
            assertEquals(2, result.getTotalIterations());
            assertEquals(5000, result.getElapsedMs());
            assertNull(result.getReport());
        }
    }

    @Nested
    @DisplayName("ReActCycle 测试")
    class ReActCycleTest {

        @Test
        @DisplayName("ReActCycle - record 创建")
        void testReActCycleCreation() {
            ReActCycle cycle = new ReActCycle("I need to search", "search AI", "AI is...");

            assertEquals("I need to search", cycle.thought());
            assertEquals("search AI", cycle.action());
            assertEquals("AI is...", cycle.observation());
        }
    }

    @Nested
    @DisplayName("DeepResearchAgent 集成测试（使用 Mock 执行器）")
    class DeepResearchAgentIntegrationTest {

        private DeepResearchAgent agent;

        @BeforeEach
        void setUp() {
            PlanExecutor planExecutor = query -> {
                List<ResearchStep> steps = new ArrayList<>();
                steps.add(ResearchStep.builder()
                        .type(ResearchStep.StepType.SEARCH)
                        .description("Search overview")
                        .searchQuery(query + " overview")
                        .status(ResearchStep.StepStatus.PENDING)
                        .build());
                steps.add(ResearchStep.builder()
                        .type(ResearchStep.StepType.SEARCH)
                        .description("Search details")
                        .searchQuery(query + " details")
                        .status(ResearchStep.StepStatus.PENDING)
                        .build());
                steps.add(ResearchStep.builder()
                        .type(ResearchStep.StepType.SYNTHESIZE)
                        .description("Synthesize report")
                        .status(ResearchStep.StepStatus.PENDING)
                        .build());
                return ResearchPlan.builder()
                        .query(query)
                        .steps(steps)
                        .currentStepIndex(0)
                        .status(ResearchPlan.PlanStatus.PENDING)
                        .build();
            };

            SearchExecutor searchExecutor = query -> List.of(
                    SearchResult.builder()
                            .title("Result for: " + query)
                            .snippet("Detailed information about " + query)
                            .source("mock-search")
                            .relevanceScore(0.9)
                            .build(),
                    SearchResult.builder()
                            .title("Analysis: " + query)
                            .snippet("In-depth analysis of " + query)
                            .source("mock-analysis")
                            .relevanceScore(0.85)
                            .build()
            );

            SynthesizeExecutor synthesizeExecutor = (query, observations, history) ->
                    "# Research Report: " + query + "\n\n## Findings\n\n" + observations + "\n\n## Conclusion\n\nResearch completed.";

            agent = new DeepResearchAgent(planExecutor, searchExecutor, synthesizeExecutor, 10);
        }

        @Test
        @DisplayName("DeepResearchAgent - 完整研究流程")
        void testFullResearchFlow() {
            ResearchResult result = agent.research("What is artificial intelligence?");

            assertTrue(result.isSuccess());
            assertNotNull(result.getReport());
            assertTrue(result.getReport().contains("artificial intelligence"));
            assertTrue(result.getTotalIterations() > 0);
            assertTrue(result.getElapsedMs() >= 0);
            assertNotNull(result.getSources());
            assertFalse(result.getSources().isEmpty());
        }

        @Test
        @DisplayName("DeepResearchAgent - 研究结果包含搜索来源")
        void testResearchResultContainsSources() {
            ResearchResult result = agent.research("quantum computing");

            assertTrue(result.isSuccess());
            assertNotNull(result.getSources());
            assertFalse(result.getSources().isEmpty());

            boolean hasMockSource = result.getSources().stream()
                    .anyMatch(s -> "mock-search".equals(s.getSource()) || "mock-analysis".equals(s.getSource()));
            assertTrue(hasMockSource, "Should contain mock search results");
        }

        @Test
        @DisplayName("DeepResearchAgent - 多次研究调用")
        void testMultipleResearchCalls() {
            ResearchResult result1 = agent.research("Topic A");
            ResearchResult result2 = agent.research("Topic B");

            assertTrue(result1.isSuccess());
            assertTrue(result2.isSuccess());
            assertNotEquals(result1.getQuery(), result2.getQuery());
        }

        @Test
        @DisplayName("DeepResearchAgent - researchAsText 返回文本报告")
        void testResearchAsText() {
            String report = agent.researchAsText("machine learning");

            assertNotNull(report);
            assertFalse(report.isEmpty());
            assertTrue(report.contains("machine learning"));
        }
    }

    @Nested
    @DisplayName("DeepResearchAgent 图结构测试")
    class DeepResearchGraphTest {

        @Test
        @DisplayName("DeepResearchAgent - 构建不抛异常")
        void testBuildAgent() {
            PlanExecutor planExecutor = query -> ResearchPlan.builder()
                    .query(query)
                    .steps(List.of(
                            ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                                    .description("Search").searchQuery(query).build()
                    ))
                    .build();

            SearchExecutor searchExecutor = query -> List.of(
                    SearchResult.builder().title("Test").snippet("Test result").source("test").build()
            );

            SynthesizeExecutor synthesizeExecutor = (q, obs, hist) -> "Report for: " + q;

            assertDoesNotThrow(() -> {
                DeepResearchAgent agent = new DeepResearchAgent(planExecutor, searchExecutor, synthesizeExecutor, 5);
                agent.research("test");
            });
        }

        @Test
        @DisplayName("DeepResearchAgent - 空计划直接进入合成")
        void testEmptyPlanGoesToSynthesize() {
            AtomicInteger planCallCount = new AtomicInteger(0);
            AtomicInteger synthesizeCallCount = new AtomicInteger(0);

            PlanExecutor planExecutor = query -> {
                planCallCount.incrementAndGet();
                return ResearchPlan.builder()
                        .query(query)
                        .steps(new ArrayList<>())
                        .build();
            };

            SearchExecutor searchExecutor = query -> List.of(
                    SearchResult.builder().title("Test").snippet("Result").source("test").build()
            );

            SynthesizeExecutor synthesizeExecutor = (q, obs, hist) -> {
                synthesizeCallCount.incrementAndGet();
                return "Synthesized report";
            };

            DeepResearchAgent agent = new DeepResearchAgent(planExecutor, searchExecutor, synthesizeExecutor, 5);
            ResearchResult result = agent.research("test query");

            assertTrue(result.isSuccess());
            assertEquals(1, planCallCount.get());
            assertTrue(synthesizeCallCount.get() >= 1, "Synthesize should be called at least once");
        }
    }

    @Nested
    @DisplayName("DeepResearchAutoConfiguration 测试")
    class AutoConfigurationTest {

        @Test
        @DisplayName("DefaultPlanExecutor - 创建默认计划")
        void testDefaultPlanExecutor() {
            DeepResearchAutoConfiguration config = new DeepResearchAutoConfiguration();
            PlanExecutor executor = config.planExecutor();

            ResearchPlan plan = executor.createPlan("test query");

            assertNotNull(plan);
            assertEquals("test query", plan.getQuery());
            assertFalse(plan.getSteps().isEmpty());
            assertTrue(plan.getSteps().stream()
                    .anyMatch(s -> s.getType() == ResearchStep.StepType.SEARCH));
        }

        @Test
        @DisplayName("DefaultSearchExecutor - 返回默认搜索结果")
        void testDefaultSearchExecutor() {
            DeepResearchAutoConfiguration config = new DeepResearchAutoConfiguration();
            SearchExecutor executor = config.searchExecutor();

            List<SearchResult> results = executor.search("test query");

            assertNotNull(results);
            assertFalse(results.isEmpty());
        }

        @Test
        @DisplayName("DefaultSynthesizeExecutor - 生成默认报告")
        void testDefaultSynthesizeExecutor() {
            DeepResearchAutoConfiguration config = new DeepResearchAutoConfiguration();
            SynthesizeExecutor executor = config.synthesizeExecutor();

            String report = executor.synthesize("test query", "Some observations", "Cycle history");

            assertNotNull(report);
            assertTrue(report.contains("test query"));
        }
    }

    @Nested
    @DisplayName("ReAct Loop 行为验证测试")
    class ReActLoopBehaviorTest {

        @Test
        @DisplayName("ReAct Loop - 迭代次数不超过最大值")
        void testIterationLimit() {
            PlanExecutor planExecutor = query -> {
                List<ResearchStep> steps = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    steps.add(ResearchStep.builder()
                            .type(ResearchStep.StepType.SEARCH)
                            .description("Search step " + i)
                            .searchQuery(query + " step " + i)
                            .build());
                }
                return ResearchPlan.builder().query(query).steps(steps).build();
            };

            SearchExecutor searchExecutor = query -> List.of(
                    SearchResult.builder().title("R").snippet("S").source("test").build()
            );

            SynthesizeExecutor synthesizeExecutor = (q, obs, hist) -> "Report";

            DeepResearchAgent agent = new DeepResearchAgent(planExecutor, searchExecutor, synthesizeExecutor, 5);
            ResearchResult result = agent.research("test");

            assertTrue(result.isSuccess());
            assertTrue(result.getTotalIterations() <= 5,
                    "Iterations should not exceed max: " + result.getTotalIterations());
        }

        @Test
        @DisplayName("ReAct Loop - 搜索结果在迭代间累积")
        void testSearchResultsAccumulate() {
            List<List<SearchResult>> allCollectedResults = new ArrayList<>();

            PlanExecutor planExecutor = query -> ResearchPlan.builder()
                    .query(query)
                    .steps(List.of(
                            ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                                    .description("S1").searchQuery("q1").build(),
                            ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                                    .description("S2").searchQuery("q2").build()
                    ))
                    .build();

            SearchExecutor searchExecutor = query -> {
                List<SearchResult> results = List.of(
                        SearchResult.builder().title("Result for " + query)
                                .snippet("Content for " + query).source("test").build()
                );
                allCollectedResults.add(results);
                return results;
            };

            SynthesizeExecutor synthesizeExecutor = (q, obs, hist) -> "Report with: " + obs;

            DeepResearchAgent agent = new DeepResearchAgent(planExecutor, searchExecutor, synthesizeExecutor, 10);
            ResearchResult result = agent.research("test accumulation");

            assertTrue(result.isSuccess());
            assertTrue(allCollectedResults.size() >= 2,
                    "Should have at least 2 search calls, got: " + allCollectedResults.size());
        }

        @Test
        @DisplayName("ReAct Loop - Plan → Search → Synthesize 三阶段执行")
        void testThreePhasePipeline() {
            List<String> phaseLog = new ArrayList<>();

            PlanExecutor planExecutor = query -> {
                phaseLog.add("PLAN");
                return ResearchPlan.builder()
                        .query(query)
                        .steps(List.of(
                                ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                                        .description("Search").searchQuery(query).build()
                        ))
                        .build();
            };

            SearchExecutor searchExecutor = query -> {
                phaseLog.add("SEARCH");
                return List.of(SearchResult.builder().title("R").snippet("S").source("test").build());
            };

            SynthesizeExecutor synthesizeExecutor = (q, obs, hist) -> {
                phaseLog.add("SYNTHESIZE");
                return "Final report";
            };

            DeepResearchAgent agent = new DeepResearchAgent(planExecutor, searchExecutor, synthesizeExecutor, 10);
            ResearchResult result = agent.research("three phase test");

            assertTrue(result.isSuccess());
            assertTrue(phaseLog.contains("PLAN"), "Should execute PLAN phase");
            assertTrue(phaseLog.contains("SEARCH"), "Should execute SEARCH phase");
            assertTrue(phaseLog.contains("SYNTHESIZE"), "Should execute SYNTHESIZE phase");

            int planIndex = phaseLog.indexOf("PLAN");
            int searchIndex = phaseLog.indexOf("SEARCH");
            int synthesizeIndex = phaseLog.indexOf("SYNTHESIZE");

            assertTrue(planIndex < searchIndex, "PLAN should execute before SEARCH");
            assertTrue(searchIndex < synthesizeIndex, "SEARCH should execute before SYNTHESIZE");
        }
    }
}
