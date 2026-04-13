package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class PlanningAgent {

    private final PlanExecutor planExecutor;
    private final SearchExecutor searchExecutor;
    private final SynthesizeExecutor synthesizeExecutor;
    private final PlanPersistenceService persistenceService;
    private final int maxIterations;

    public PlanningAgent(PlanExecutor planExecutor,
                         SearchExecutor searchExecutor,
                         SynthesizeExecutor synthesizeExecutor,
                         PlanPersistenceService persistenceService,
                         int maxIterations) {
        this.planExecutor = planExecutor;
        this.searchExecutor = searchExecutor;
        this.synthesizeExecutor = synthesizeExecutor;
        this.persistenceService = persistenceService;
        this.maxIterations = maxIterations;
    }

    public PlanningAgent(PlanExecutor planExecutor,
                         SearchExecutor searchExecutor,
                         SynthesizeExecutor synthesizeExecutor,
                         PlanPersistenceService persistenceService) {
        this(planExecutor, searchExecutor, synthesizeExecutor, persistenceService, 10);
    }

    public PlanningResult generateAndPersist(String query) {
        log.info("[PlanningAgent] Generating and persisting plan for: {}", query);
        long startTime = System.currentTimeMillis();

        try {
            ResearchPlan plan = planExecutor.createPlan(query);
            String planId = persistenceService.savePlan(plan);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[PlanningAgent] Plan generated and persisted: planId={}, steps={}", planId, plan.getStepCount());

            return PlanningResult.builder()
                    .planId(planId)
                    .query(query)
                    .plan(plan)
                    .elapsedMs(elapsed)
                    .success(true)
                    .build();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[PlanningAgent] Failed to generate plan: {}", e.getMessage(), e);
            return PlanningResult.builder()
                    .query(query)
                    .elapsedMs(elapsed)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    public ResearchResult executeWithPersistedPlan(String planId) {
        log.info("[PlanningAgent] Executing with persisted plan: planId={}", planId);
        long startTime = System.currentTimeMillis();

        try {
            ResearchPlan plan = persistenceService.loadPlan(planId);
            if (plan == null) {
                long elapsed = System.currentTimeMillis() - startTime;
                return ResearchResult.failure("unknown", "Plan not found: " + planId, 0, elapsed);
            }

            String query = plan.getQuery();
            log.info("[PlanningAgent] Loaded plan: query={}, steps={}, currentStep={}",
                    query, plan.getStepCount(), plan.getCurrentStepIndex());

            ResearchState state = ResearchState.builder()
                    .query(query)
                    .phase(ResearchState.ReActPhase.PLAN)
                    .maxIterations(maxIterations)
                    .shouldContinue(true)
                    .cycles(new java.util.ArrayList<>())
                    .searchResults(new java.util.ArrayList<>())
                    .build();
            state.setPlan(plan);

            if (plan.getSteps() == null || plan.getSteps().isEmpty() || plan.isComplete()) {
                executeSynthesizePhase(state, planId);
            } else {
                executeReActLoopWithPersistence(state, planId);
            }

            long elapsed = System.currentTimeMillis() - startTime;

            if (state.getPhase() == ResearchState.ReActPhase.ERROR) {
                return ResearchResult.failure(query, "Research ended in error",
                        state.getIterationCount(), elapsed);
            }

            return ResearchResult.success(
                    query,
                    state.getFinalReport(),
                    state.getSearchResults(),
                    state.getIterationCount(),
                    elapsed
            );
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[PlanningAgent] Execution failed: {}", e.getMessage(), e);
            return ResearchResult.failure("unknown", e.getMessage(), 0, elapsed);
        }
    }

    public ResearchResult researchWithPersistence(String query) {
        log.info("[PlanningAgent] Starting research with persistence: {}", query);

        PlanningResult planResult = generateAndPersist(query);
        if (!planResult.isSuccess()) {
            return ResearchResult.failure(query, "Plan generation failed: " + planResult.getErrorMessage(), 0, 0);
        }

        return executeWithPersistedPlan(planResult.getPlanId());
    }

    public ResearchResult resumePlan(String planId) {
        log.info("[PlanningAgent] Resuming plan: planId={}", planId);

        ResearchPlan plan = persistenceService.loadPlan(planId);
        if (plan == null) {
            return ResearchResult.failure("unknown", "Plan not found: " + planId, 0, 0);
        }

        if (plan.isComplete()) {
            log.info("[PlanningAgent] Plan already completed: planId={}", planId);
            return ResearchResult.failure(plan.getQuery(), "Plan already completed", 0, 0);
        }

        return executeWithPersistedPlan(planId);
    }

    public String getPlanContext(String planId) {
        return persistenceService.loadCurrentStepContext(planId);
    }

    public String getPlanContextFromStep(String planId, int fromStepIndex) {
        return persistenceService.loadPlanContext(planId, fromStepIndex);
    }

    private void executeReActLoopWithPersistence(ResearchState state, String planId) {
        while (state.isShouldContinue() && !state.hasExceededMaxIterations()) {
            ResearchStep currentStep = state.getPlan().getCurrentStep();
            if (currentStep == null || state.getPlan().isComplete()) {
                break;
            }

            if (currentStep.getType() == ResearchStep.StepType.SYNTHESIZE) {
                currentStep.markInProgress();
                persistenceService.updateStepResult(planId,
                        state.getPlan().getCurrentStepIndex(),
                        ResearchStep.StepStatus.IN_PROGRESS, null);
                break;
            }

            int stepIndex = state.getPlan().getCurrentStepIndex();

            executeThinkPhase(state, currentStep, planId, stepIndex);
            executeSearchPhase(state, currentStep, planId, stepIndex);
            executeObservePhase(state, currentStep, planId, stepIndex);
        }

        executeSynthesizePhase(state, planId);
    }

    private void executeThinkPhase(ResearchState state, ResearchStep step, String planId, int stepIndex) {
        state.setPhase(ResearchState.ReActPhase.THINK);
        step.markInProgress();
        state.setCurrentThought(step.getDescription());
        state.setCurrentAction(step.getSearchQuery());

        persistenceService.updateStepResult(planId, stepIndex, ResearchStep.StepStatus.IN_PROGRESS, null);

        log.info("[PlanningAgent] THINK - Step {}: {} - {}", stepIndex + 1, step.getType(), step.getDescription());
    }

    private void executeSearchPhase(ResearchState state, ResearchStep step, String planId, int stepIndex) {
        state.setPhase(ResearchState.ReActPhase.ACT);
        try {
            String searchQuery = step.getSearchQuery();
            if (searchQuery == null || searchQuery.isEmpty()) {
                searchQuery = state.getQuery();
            }

            String planContext = persistenceService.loadPlanContext(planId, stepIndex);
            log.debug("[PlanningAgent] Loaded plan context for step {}: {} chars", stepIndex, planContext.length());

            List<SearchResult> results = searchExecutor.search(searchQuery);
            for (SearchResult result : results) {
                state.addSearchResult(result);
            }
            log.info("[PlanningAgent] ACT (Search) - Query: {}, Results: {}", searchQuery, results.size());
        } catch (Exception e) {
            log.error("[PlanningAgent] Search failed: {}", e.getMessage());
            state.setCurrentObservation("Search failed: " + e.getMessage());
        }
    }

    private void executeObservePhase(ResearchState state, ResearchStep step, String planId, int stepIndex) {
        state.setPhase(ResearchState.ReActPhase.OBSERVE);

        String observation = state.getAccumulatedObservations();
        state.setCurrentObservation(observation);

        ReActCycle cycle = new ReActCycle(
                state.getCurrentThought(),
                state.getCurrentAction(),
                observation
        );
        state.addCycle(cycle);

        step.markCompleted(observation);
        state.getPlan().advanceToNextStep();

        persistenceService.updateStepResult(planId, stepIndex, ResearchStep.StepStatus.COMPLETED, truncate(observation, 500));
        persistenceService.updatePlanProgress(planId, state.getPlan().getCurrentStepIndex(), state.getPlan().getStatus());

        log.info("[PlanningAgent] OBSERVE - Progress: {}/{} (persisted)",
                state.getPlan().getCompletedStepCount(), state.getPlan().getStepCount());
    }

    private void executeSynthesizePhase(ResearchState state, String planId) {
        log.info("[PlanningAgent] Phase: SYNTHESIZE");
        state.setPhase(ResearchState.ReActPhase.SYNTHESIZE);

        try {
            String report = synthesizeExecutor.synthesize(
                    state.getQuery(),
                    state.getAccumulatedObservations(),
                    state.getCycleHistory()
            );
            state.setFinalReport(report);
            state.setPhase(ResearchState.ReActPhase.COMPLETE);

            if (state.getPlan() != null) {
                state.getPlan().setStatus(ResearchPlan.PlanStatus.COMPLETED);
                persistenceService.updatePlanProgress(planId,
                        state.getPlan().getCurrentStepIndex(),
                        ResearchPlan.PlanStatus.COMPLETED);
            }

            log.info("[PlanningAgent] Report generated: {} chars", report.length());
        } catch (Exception e) {
            log.error("[PlanningAgent] Synthesis failed: {}", e.getMessage());
            state.setPhase(ResearchState.ReActPhase.ERROR);

            if (state.getPlan() != null) {
                persistenceService.updatePlanProgress(planId,
                        state.getPlan().getCurrentStepIndex(),
                        ResearchPlan.PlanStatus.FAILED);
            }
        }
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
