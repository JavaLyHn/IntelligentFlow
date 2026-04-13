package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanningAgentTest {

    private static Path tempDir;
    private PlanPersistenceService persistenceService;
    private PlanMarkdownSerializer serializer;

    @BeforeAll
    static void setupTempDir() throws IOException {
        tempDir = Files.createTempDirectory("planning-agent-test");
    }

    @AfterAll
    static void cleanupTempDir() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
    }

    @BeforeEach
    void setUp() {
        persistenceService = new PlanPersistenceService(tempDir);
        serializer = new PlanMarkdownSerializer();
    }

    @Nested
    @DisplayName("PlanMarkdownSerializer 测试")
    class PlanMarkdownSerializerTest {

        @Test
        @DisplayName("序列化 - ResearchPlan 转 Markdown")
        void testToMarkdown() {
            List<ResearchStep> steps = List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Search overview").searchQuery("AI overview").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Search details").searchQuery("AI details").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SYNTHESIZE)
                            .description("Synthesize report").build()
            );

            ResearchPlan plan = ResearchPlan.builder()
                    .query("What is AI?")
                    .steps(steps)
                    .currentStepIndex(0)
                    .status(ResearchPlan.PlanStatus.PENDING)
                    .build();

            String markdown = serializer.toMarkdown(plan, "test-001");

            assertTrue(markdown.contains("# Research Plan"));
            assertTrue(markdown.contains("test-001"));
            assertTrue(markdown.contains("What is AI?"));
            assertTrue(markdown.contains("PENDING"));
            assertTrue(markdown.contains("Search overview"));
            assertTrue(markdown.contains("Search details"));
            assertTrue(markdown.contains("Synthesize report"));
            assertTrue(markdown.contains("SEARCH"));
            assertTrue(markdown.contains("SYNTHESIZE"));
        }

        @Test
        @DisplayName("反序列化 - Markdown 转 ResearchPlan")
        void testFromMarkdown() {
            List<ResearchStep> steps = List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Search overview").searchQuery("AI overview").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SYNTHESIZE)
                            .description("Synthesize report").build()
            );

            ResearchPlan original = ResearchPlan.builder()
                    .query("What is AI?")
                    .steps(steps)
                    .currentStepIndex(1)
                    .status(ResearchPlan.PlanStatus.IN_PROGRESS)
                    .build();

            String markdown = serializer.toMarkdown(original, "test-002");
            ResearchPlan restored = serializer.fromMarkdown(markdown);

            assertNotNull(restored);
            assertEquals("What is AI?", restored.getQuery());
            assertEquals(2, restored.getStepCount());
            assertEquals(1, restored.getCurrentStepIndex());
            assertEquals(ResearchPlan.PlanStatus.IN_PROGRESS, restored.getStatus());
            assertEquals("Search overview", restored.getSteps().get(0).getDescription());
            assertEquals(ResearchStep.StepType.SEARCH, restored.getSteps().get(0).getType());
            assertEquals("AI overview", restored.getSteps().get(0).getSearchQuery());
        }

        @Test
        @DisplayName("提取 PlanId")
        void testExtractPlanId() {
            ResearchPlan plan = ResearchPlan.builder()
                    .query("test").steps(List.of()).build();

            String markdown = serializer.toMarkdown(plan, "abc-123");
            String planId = serializer.extractPlanId(markdown);

            assertEquals("abc-123", planId);
        }

        @Test
        @DisplayName("构建执行上下文 - 按需加载")
        void testBuildExecutionContext() {
            List<ResearchStep> steps = List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 1").searchQuery("query1").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 2").searchQuery("query2").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SYNTHESIZE)
                            .description("Step 3").build()
            );

            steps.get(0).markCompleted("Result 1");
            steps.get(1).markInProgress();

            ResearchPlan plan = ResearchPlan.builder()
                    .query("Research topic")
                    .steps(steps)
                    .currentStepIndex(1)
                    .status(ResearchPlan.PlanStatus.IN_PROGRESS)
                    .build();

            String context = serializer.buildExecutionContext(plan, 1);

            assertTrue(context.contains("# Current Research Context"));
            assertTrue(context.contains("Research topic"));
            assertTrue(context.contains("COMPLETED"));
            assertTrue(context.contains("**[CURRENT]**"));
            assertTrue(context.contains("Step 2"));
            assertTrue(context.contains("Step 3"));
        }

        @Test
        @DisplayName("序列化/反序列化往返一致性")
        void testRoundTrip() {
            List<ResearchStep> steps = List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Search step").searchQuery("test query").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SYNTHESIZE)
                            .description("Final synthesis").build()
            );

            ResearchPlan original = ResearchPlan.builder()
                    .query("Round trip test")
                    .steps(steps)
                    .currentStepIndex(0)
                    .status(ResearchPlan.PlanStatus.PENDING)
                    .build();

            String markdown = serializer.toMarkdown(original, "round-trip");
            ResearchPlan restored = serializer.fromMarkdown(markdown);

            assertEquals(original.getQuery(), restored.getQuery());
            assertEquals(original.getStepCount(), restored.getStepCount());
            assertEquals(original.getCurrentStepIndex(), restored.getCurrentStepIndex());
            assertEquals(original.getStatus(), restored.getStatus());
        }

        @Test
        @DisplayName("空 Markdown 返回 null")
        void testFromEmptyMarkdown() {
            assertNull(serializer.fromMarkdown(null));
            assertNull(serializer.fromMarkdown(""));
        }

        @Test
        @DisplayName("特殊字符转义")
        void testSpecialCharacterEscaping() {
            ResearchPlan plan = ResearchPlan.builder()
                    .query("What is AI | Machine Learning")
                    .steps(List.of(
                            ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                                    .description("Search AI|ML").searchQuery("AI|ML").build()
                    ))
                    .build();

            String markdown = serializer.toMarkdown(plan, "special-chars");
            ResearchPlan restored = serializer.fromMarkdown(markdown);

            assertNotNull(restored);
            assertTrue(restored.getQuery().contains("|"));
        }
    }

    @Nested
    @DisplayName("PlanPersistenceService 测试")
    class PlanPersistenceServiceTest {

        @Test
        @DisplayName("保存和加载计划")
        void testSaveAndLoadPlan() {
            List<ResearchStep> steps = List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Search step").searchQuery("test").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SYNTHESIZE)
                            .description("Synthesize").build()
            );

            ResearchPlan plan = ResearchPlan.builder()
                    .query("Persistence test")
                    .steps(steps)
                    .currentStepIndex(0)
                    .status(ResearchPlan.PlanStatus.PENDING)
                    .build();

            String planId = persistenceService.savePlan(plan);
            assertNotNull(planId);
            assertFalse(planId.isEmpty());

            ResearchPlan loaded = persistenceService.loadPlan(planId);
            assertNotNull(loaded);
            assertEquals("Persistence test", loaded.getQuery());
            assertEquals(2, loaded.getStepCount());
        }

        @Test
        @DisplayName("使用指定 planId 保存计划")
        void testSavePlanWithSpecificId() {
            ResearchPlan plan = ResearchPlan.builder()
                    .query("Specific ID test")
                    .steps(List.of())
                    .build();

            persistenceService.savePlan(plan, "my-custom-id");

            assertTrue(persistenceService.planExists("my-custom-id"));

            ResearchPlan loaded = persistenceService.loadPlan("my-custom-id");
            assertNotNull(loaded);
            assertEquals("Specific ID test", loaded.getQuery());
        }

        @Test
        @DisplayName("加载不存在的计划返回 null")
        void testLoadNonExistentPlan() {
            ResearchPlan loaded = persistenceService.loadPlan("non-existent");
            assertNull(loaded);
        }

        @Test
        @DisplayName("更新计划进度")
        void testUpdatePlanProgress() {
            List<ResearchStep> steps = List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 1").searchQuery("q1").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 2").searchQuery("q2").build()
            );

            ResearchPlan plan = ResearchPlan.builder()
                    .query("Progress test")
                    .steps(steps)
                    .currentStepIndex(0)
                    .status(ResearchPlan.PlanStatus.PENDING)
                    .build();

            String planId = persistenceService.savePlan(plan);

            persistenceService.updatePlanProgress(planId, 1, ResearchPlan.PlanStatus.IN_PROGRESS);

            ResearchPlan updated = persistenceService.loadPlan(planId);
            assertNotNull(updated);
            assertEquals(1, updated.getCurrentStepIndex());
            assertEquals(ResearchPlan.PlanStatus.IN_PROGRESS, updated.getStatus());
        }

        @Test
        @DisplayName("更新步骤结果")
        void testUpdateStepResult() {
            List<ResearchStep> steps = new ArrayList<>(List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 1").searchQuery("q1").build()
            ));

            ResearchPlan plan = ResearchPlan.builder()
                    .query("Step result test")
                    .steps(steps)
                    .currentStepIndex(0)
                    .status(ResearchPlan.PlanStatus.PENDING)
                    .build();

            String planId = persistenceService.savePlan(plan);

            persistenceService.updateStepResult(planId, 0, ResearchStep.StepStatus.COMPLETED, "Found results");

            ResearchPlan updated = persistenceService.loadPlan(planId);
            assertNotNull(updated);
            assertEquals(ResearchStep.StepStatus.COMPLETED, updated.getSteps().get(0).getStatus());
            assertEquals("Found results", updated.getSteps().get(0).getResult());
        }

        @Test
        @DisplayName("列出所有计划")
        void testListPlans() {
            ResearchPlan plan1 = ResearchPlan.builder().query("Plan 1").steps(List.of()).build();
            ResearchPlan plan2 = ResearchPlan.builder().query("Plan 2").steps(List.of()).build();

            persistenceService.savePlan(plan1, "list-test-1");
            persistenceService.savePlan(plan2, "list-test-2");

            List<String> plans = persistenceService.listPlans();
            assertTrue(plans.contains("list-test-1"));
            assertTrue(plans.contains("list-test-2"));
        }

        @Test
        @DisplayName("删除计划")
        void testDeletePlan() {
            ResearchPlan plan = ResearchPlan.builder().query("Delete test").steps(List.of()).build();
            String planId = persistenceService.savePlan(plan, "delete-me");

            assertTrue(persistenceService.planExists("delete-me"));

            boolean deleted = persistenceService.deletePlan("delete-me");
            assertTrue(deleted);
            assertFalse(persistenceService.planExists("delete-me"));
        }

        @Test
        @DisplayName("按需加载计划上下文")
        void testLoadPlanContext() {
            List<ResearchStep> steps = List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 1").searchQuery("q1").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 2").searchQuery("q2").build()
            );

            steps.get(0).markCompleted("Result 1");

            ResearchPlan plan = ResearchPlan.builder()
                    .query("Context test")
                    .steps(steps)
                    .currentStepIndex(1)
                    .status(ResearchPlan.PlanStatus.IN_PROGRESS)
                    .build();

            String planId = persistenceService.savePlan(plan, "context-test");

            String context = persistenceService.loadPlanContext(planId, 1);
            assertTrue(context.contains("# Current Research Context"));
            assertTrue(context.contains("Context test"));
            assertTrue(context.contains("COMPLETED"));
            assertTrue(context.contains("**[CURRENT]**"));
        }

        @Test
        @DisplayName("加载当前步骤上下文")
        void testLoadCurrentStepContext() {
            List<ResearchStep> steps = List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 1").searchQuery("q1").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SYNTHESIZE)
                            .description("Step 2").build()
            );

            ResearchPlan plan = ResearchPlan.builder()
                    .query("Current step test")
                    .steps(steps)
                    .currentStepIndex(0)
                    .status(ResearchPlan.PlanStatus.PENDING)
                    .build();

            String planId = persistenceService.savePlan(plan, "current-step-test");

            String context = persistenceService.loadCurrentStepContext(planId);
            assertTrue(context.contains("Current step test"));
            assertTrue(context.contains("**[CURRENT]**"));
        }

        @Test
        @DisplayName("plan.md 文件实际存在且内容正确")
        void testPlanFileExists() throws IOException {
            ResearchPlan plan = ResearchPlan.builder()
                    .query("File existence test")
                    .steps(List.of(
                            ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                                    .description("Search").searchQuery("test").build()
                    ))
                    .build();

            String planId = persistenceService.savePlan(plan, "file-exist-test");

            Path planFile = tempDir.resolve("plan-file-exist-test.md");
            assertTrue(Files.exists(planFile), "plan.md file should exist");

            String content = Files.readString(planFile, StandardCharsets.UTF_8);
            assertTrue(content.contains("# Research Plan"));
            assertTrue(content.contains("File existence test"));
            assertTrue(content.contains("file-exist-test"));
        }
    }

    @Nested
    @DisplayName("PlanningResult 测试")
    class PlanningResultTest {

        @Test
        @DisplayName("PlanningResult - 成功工厂方法")
        void testSuccessFactory() {
            ResearchPlan plan = ResearchPlan.builder().query("test").steps(List.of()).build();
            PlanningResult result = PlanningResult.success("plan-1", "test", plan, 100);

            assertTrue(result.isSuccess());
            assertEquals("plan-1", result.getPlanId());
            assertEquals("test", result.getQuery());
            assertEquals(100, result.getElapsedMs());
        }

        @Test
        @DisplayName("PlanningResult - 失败工厂方法")
        void testFailureFactory() {
            PlanningResult result = PlanningResult.failure("test", "Plan failed", 50);

            assertFalse(result.isSuccess());
            assertEquals("test", result.getQuery());
            assertEquals("Plan failed", result.getErrorMessage());
        }
    }

    @Nested
    @DisplayName("PlanningAgent 集成测试")
    class PlanningAgentIntegrationTest {

        private PlanningAgent agent;

        @BeforeEach
        void setUpAgent() {
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
                            .snippet("Information about " + query)
                            .source("mock")
                            .relevanceScore(0.9)
                            .build()
            );

            SynthesizeExecutor synthesizeExecutor = (query, observations, history) ->
                    "# Report: " + query + "\n\n" + observations;

            agent = new PlanningAgent(planExecutor, searchExecutor, synthesizeExecutor, persistenceService, 10);
        }

        @Test
        @DisplayName("PlanningAgent - 生成并持久化计划")
        void testGenerateAndPersist() {
            PlanningResult result = agent.generateAndPersist("What is quantum computing?");

            assertTrue(result.isSuccess());
            assertNotNull(result.getPlanId());
            assertFalse(result.getPlanId().isEmpty());
            assertNotNull(result.getPlan());
            assertEquals("What is quantum computing?", result.getQuery());
            assertEquals(3, result.getPlan().getStepCount());

            assertTrue(persistenceService.planExists(result.getPlanId()));
        }

        @Test
        @DisplayName("PlanningAgent - 持久化后计划可从文件加载")
        void testPersistedPlanCanBeLoaded() {
            PlanningResult result = agent.generateAndPersist("AI safety research");

            ResearchPlan loaded = persistenceService.loadPlan(result.getPlanId());
            assertNotNull(loaded);
            assertEquals("AI safety research", loaded.getQuery());
            assertEquals(3, loaded.getStepCount());
        }

        @Test
        @DisplayName("PlanningAgent - 使用持久化计划执行研究")
        void testExecuteWithPersistedPlan() {
            PlanningResult planResult = agent.generateAndPersist("Deep learning trends");
            assertTrue(planResult.isSuccess());

            ResearchResult result = agent.executeWithPersistedPlan(planResult.getPlanId());

            assertTrue(result.isSuccess());
            assertNotNull(result.getReport());
            assertTrue(result.getReport().contains("Deep learning trends"));
            assertTrue(result.getTotalIterations() > 0);
        }

        @Test
        @DisplayName("PlanningAgent - 完整流程：生成+持久化+执行")
        void testResearchWithPersistence() {
            ResearchResult result = agent.researchWithPersistence("Machine learning applications");

            assertTrue(result.isSuccess());
            assertNotNull(result.getReport());
            assertTrue(result.getReport().contains("Machine learning applications"));
            assertNotNull(result.getSources());
            assertFalse(result.getSources().isEmpty());
        }

        @Test
        @DisplayName("PlanningAgent - 执行过程中计划进度被持久化")
        void testPlanProgressPersistedDuringExecution() {
            PlanningResult planResult = agent.generateAndPersist("Blockchain technology");
            String planId = planResult.getPlanId();

            agent.executeWithPersistedPlan(planId);

            ResearchPlan completedPlan = persistenceService.loadPlan(planId);
            assertNotNull(completedPlan);
            assertEquals(ResearchPlan.PlanStatus.COMPLETED, completedPlan.getStatus());
        }

        @Test
        @DisplayName("PlanningAgent - 按需加载计划上下文")
        void testGetPlanContext() {
            PlanningResult planResult = agent.generateAndPersist("Neural networks");
            String planId = planResult.getPlanId();

            String context = agent.getPlanContext(planId);
            assertTrue(context.contains("# Current Research Context"));
            assertTrue(context.contains("Neural networks"));
        }

        @Test
        @DisplayName("PlanningAgent - 从指定步骤加载上下文")
        void testGetPlanContextFromStep() {
            PlanningResult planResult = agent.generateAndPersist("Transformer architecture");
            String planId = planResult.getPlanId();

            String context = agent.getPlanContextFromStep(planId, 1);
            assertTrue(context.contains("# Current Research Context"));
            assertTrue(context.contains("Transformer architecture"));
        }

        @Test
        @DisplayName("PlanningAgent - 执行不存在的计划返回失败")
        void testExecuteNonExistentPlan() {
            ResearchResult result = agent.executeWithPersistedPlan("non-existent-id");

            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("Plan not found"));
        }

        @Test
        @DisplayName("PlanningAgent - 恢复已完成计划返回失败")
        void testResumeCompletedPlan() {
            PlanningResult planResult = agent.generateAndPersist("Completed topic");
            String planId = planResult.getPlanId();

            agent.executeWithPersistedPlan(planId);

            ResearchResult resumeResult = agent.resumePlan(planId);
            assertFalse(resumeResult.isSuccess());
        }

        @Test
        @DisplayName("PlanningAgent - 多个计划独立管理")
        void testMultiplePlansIndependentlyManaged() {
            PlanningResult result1 = agent.generateAndPersist("Topic A");
            PlanningResult result2 = agent.generateAndPersist("Topic B");

            assertNotEquals(result1.getPlanId(), result2.getPlanId());

            ResearchPlan plan1 = persistenceService.loadPlan(result1.getPlanId());
            ResearchPlan plan2 = persistenceService.loadPlan(result2.getPlanId());

            assertEquals("Topic A", plan1.getQuery());
            assertEquals("Topic B", plan2.getQuery());
        }
    }

    @Nested
    @DisplayName("PlanningAgent 按需加载避免历史叠加测试")
    class OnDemandLoadingTest {

        @Test
        @DisplayName("按需加载 - 仅加载当前步骤上下文而非完整历史")
        void testOnDemandLoadingAvoidsFullHistory() {
            List<ResearchStep> steps = new ArrayList<>(List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 1").searchQuery("q1").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 2").searchQuery("q2").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 3").searchQuery("q3").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SYNTHESIZE)
                            .description("Final").build()
            ));

            String longResult1 = "Result 1: " + "A".repeat(300) + " end of result 1";
            String longResult2 = "Result 2: " + "B".repeat(300) + " end of result 2";
            steps.get(0).markCompleted(longResult1);
            steps.get(1).markCompleted(longResult2);

            ResearchPlan plan = ResearchPlan.builder()
                    .query("On-demand loading test")
                    .steps(steps)
                    .currentStepIndex(2)
                    .status(ResearchPlan.PlanStatus.IN_PROGRESS)
                    .build();

            String planId = persistenceService.savePlan(plan, "on-demand-test");

            String contextFromStart = persistenceService.loadPlanContext(planId, 0);
            String contextFromStep2 = persistenceService.loadPlanContext(planId, 2);

            assertTrue(contextFromStart.contains("Step 1"));
            assertTrue(contextFromStart.contains("Step 2"));
            assertTrue(contextFromStart.contains("Step 3"));

            assertTrue(contextFromStep2.contains("**[CURRENT]**"));
            assertTrue(contextFromStep2.contains("Step 3"));
            assertTrue(contextFromStep2.contains("Step 4"));

            assertTrue(contextFromStep2.contains("COMPLETED"));
            assertTrue(contextFromStep2.contains("Result Summary"));
        }

        @Test
        @DisplayName("按需加载 - 执行上下文包含查询和计划概览")
        void testExecutionContextContainsEssentialInfo() {
            ResearchPlan plan = ResearchPlan.builder()
                    .query("Essential info test")
                    .steps(List.of(
                            ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                                    .description("Search").searchQuery("test").build()
                    ))
                    .currentStepIndex(0)
                    .status(ResearchPlan.PlanStatus.PENDING)
                    .build();

            String planId = persistenceService.savePlan(plan, "essential-info-test");

            String context = persistenceService.loadCurrentStepContext(planId);

            assertTrue(context.contains("Essential info test"), "Should contain query");
            assertTrue(context.contains("Total Steps"), "Should contain plan overview");
            assertTrue(context.contains("Current Step"), "Should contain current step info");
        }

        @Test
        @DisplayName("按需加载 - 已完成步骤仅保留摘要")
        void testCompletedStepsHaveSummaries() {
            List<ResearchStep> steps = List.of(
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 1").searchQuery("q1").build(),
                    ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                            .description("Step 2").searchQuery("q2").build()
            );

            String longResult = "A".repeat(500);
            steps.get(0).markCompleted(longResult);

            ResearchPlan plan = ResearchPlan.builder()
                    .query("Summary test")
                    .steps(steps)
                    .currentStepIndex(1)
                    .status(ResearchPlan.PlanStatus.IN_PROGRESS)
                    .build();

            String planId = persistenceService.savePlan(plan, "summary-test");

            String context = persistenceService.loadPlanContext(planId, 1);

            assertTrue(context.contains("Result Summary"));
            assertTrue(context.contains("COMPLETED"));
        }
    }

    @Nested
    @DisplayName("PlanningAgentAutoConfiguration 测试")
    class AutoConfigurationTest {

        @Test
        @DisplayName("FallbackPlanExecutor - 创建默认计划")
        void testFallbackPlanExecutor() {
            PlanExecutor fallback = new PlanningAgentAutoConfiguration.FallbackPlanExecutor();
            ResearchPlan plan = fallback.createPlan("test query");

            assertNotNull(plan);
            assertEquals("test query", plan.getQuery());
            assertFalse(plan.getSteps().isEmpty());
        }

        @Test
        @DisplayName("FallbackSearchExecutor - 返回默认搜索结果")
        void testFallbackSearchExecutor() {
            SearchExecutor fallback = new PlanningAgentAutoConfiguration.FallbackSearchExecutor();
            List<SearchResult> results = fallback.search("test");

            assertNotNull(results);
            assertFalse(results.isEmpty());
        }

        @Test
        @DisplayName("FallbackSynthesizeExecutor - 生成默认报告")
        void testFallbackSynthesizeExecutor() {
            SynthesizeExecutor fallback = new PlanningAgentAutoConfiguration.FallbackSynthesizeExecutor();
            String report = fallback.synthesize("test", "observations", "history");

            assertNotNull(report);
            assertTrue(report.contains("test"));
        }
    }

    @Nested
    @DisplayName("端到端场景测试")
    class EndToEndScenarioTest {

        @Test
        @DisplayName("场景：生成计划 → 持久化 → 按需加载 → 执行 → 完成后状态正确")
        void testEndToEndScenario() {
            PlanExecutor planExecutor = query -> {
                List<ResearchStep> steps = new ArrayList<>();
                steps.add(ResearchStep.builder()
                        .type(ResearchStep.StepType.SEARCH)
                        .description("Research background")
                        .searchQuery(query + " background")
                        .build());
                steps.add(ResearchStep.builder()
                        .type(ResearchStep.StepType.SEARCH)
                        .description("Research latest developments")
                        .searchQuery(query + " latest")
                        .build());
                steps.add(ResearchStep.builder()
                        .type(ResearchStep.StepType.SYNTHESIZE)
                        .description("Write comprehensive report")
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
                            .title("Search: " + query)
                            .snippet("Content for " + query)
                            .source("test")
                            .relevanceScore(0.85)
                            .build()
            );

            SynthesizeExecutor synthesizeExecutor = (query, observations, history) ->
                    "# Research Report\n\n## " + query + "\n\n" + observations;

            PlanningAgent agent = new PlanningAgent(
                    planExecutor, searchExecutor, synthesizeExecutor, persistenceService, 10);

            PlanningResult planResult = agent.generateAndPersist("Artificial General Intelligence");
            assertTrue(planResult.isSuccess());
            String planId = planResult.getPlanId();

            ResearchPlan loadedPlan = persistenceService.loadPlan(planId);
            assertNotNull(loadedPlan);
            assertEquals(ResearchPlan.PlanStatus.PENDING, loadedPlan.getStatus());

            String context = agent.getPlanContext(planId);
            assertTrue(context.contains("Artificial General Intelligence"));

            ResearchResult researchResult = agent.executeWithPersistedPlan(planId);
            assertTrue(researchResult.isSuccess());
            assertTrue(researchResult.getReport().contains("Artificial General Intelligence"));

            ResearchPlan completedPlan = persistenceService.loadPlan(planId);
            assertNotNull(completedPlan);
            assertEquals(ResearchPlan.PlanStatus.COMPLETED, completedPlan.getStatus());

            List<String> allPlans = persistenceService.listPlans();
            assertTrue(allPlans.contains(planId));
        }

        @Test
        @DisplayName("场景：长任务执行中计划进度实时持久化")
        void testProgressPersistedDuringLongTask() {
            List<String> persistedStatuses = new ArrayList<>();

            PlanExecutor planExecutor = query -> ResearchPlan.builder()
                    .query(query)
                    .steps(List.of(
                            ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                                    .description("S1").searchQuery("q1").build(),
                            ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                                    .description("S2").searchQuery("q2").build(),
                            ResearchStep.builder().type(ResearchStep.StepType.SYNTHESIZE)
                                    .description("Final").build()
                    ))
                    .currentStepIndex(0)
                    .status(ResearchPlan.PlanStatus.PENDING)
                    .build();

            SearchExecutor searchExecutor = query -> {
                ResearchPlan currentPlan = persistenceService.loadPlan(
                        persistenceService.listPlans().stream().findFirst().orElse(""));
                if (currentPlan != null) {
                    persistedStatuses.add(currentPlan.getStatus().name() + ":" + currentPlan.getCurrentStepIndex());
                }
                return List.of(SearchResult.builder()
                        .title("R").snippet("S").source("test").build());
            };

            SynthesizeExecutor synthesizeExecutor = (q, obs, hist) -> "Report";

            PlanningAgent agent = new PlanningAgent(
                    planExecutor, searchExecutor, synthesizeExecutor, persistenceService, 10);

            agent.researchWithPersistence("Long task test");

            assertFalse(persistedStatuses.isEmpty(), "Should have tracked progress during execution");
        }
    }
}
