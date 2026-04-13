package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;

import java.util.*;

@Slf4j
public class DeepResearchAgent {

    private final PlanExecutor planExecutor;
    private final SearchExecutor searchExecutor;
    private final SynthesizeExecutor synthesizeExecutor;
    private final int maxIterations;

    public DeepResearchAgent(PlanExecutor planExecutor,
                             SearchExecutor searchExecutor,
                             SynthesizeExecutor synthesizeExecutor,
                             int maxIterations) {
        this.planExecutor = planExecutor;
        this.searchExecutor = searchExecutor;
        this.synthesizeExecutor = synthesizeExecutor;
        this.maxIterations = maxIterations;
    }

    public DeepResearchAgent(PlanExecutor planExecutor,
                             SearchExecutor searchExecutor,
                             SynthesizeExecutor synthesizeExecutor) {
        this(planExecutor, searchExecutor, synthesizeExecutor, 10);
    }

    public ResearchResult research(String query) {
        long startTime = System.currentTimeMillis();
        log.info("[DeepResearchAgent] Starting research: {}", query);

        try {
            ResearchState state = ResearchState.builder()
                    .query(query)
                    .phase(ResearchState.ReActPhase.INIT)
                    .maxIterations(maxIterations)
                    .shouldContinue(true)
                    .cycles(new ArrayList<>())
                    .searchResults(new ArrayList<>())
                    .build();

            executePlanPhase(state);

            if (state.getPhase() == ResearchState.ReActPhase.ERROR) {
                long elapsed = System.currentTimeMillis() - startTime;
                return ResearchResult.failure(query, "Plan phase failed", state.getIterationCount(), elapsed);
            }

            if (state.getPlan() == null || state.getPlan().isComplete() ||
                    state.getPlan().getSteps() == null || state.getPlan().getSteps().isEmpty()) {
                executeSynthesizePhase(state);
            } else {
                executeReActLoop(state);
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
            log.error("[DeepResearchAgent] Research failed: {}", e.getMessage(), e);
            return ResearchResult.failure(query, e.getMessage(), 0, elapsed);
        }
    }

    private void executePlanPhase(ResearchState state) {
        log.info("[DeepResearchAgent] Phase: PLAN - Query: {}", state.getQuery());
        state.setPhase(ResearchState.ReActPhase.PLAN);

        try {
            ResearchPlan plan = planExecutor.createPlan(state.getQuery());
            state.setPlan(plan);
            state.getPlan().setStatus(ResearchPlan.PlanStatus.IN_PROGRESS);
            log.info("[DeepResearchAgent] Plan created with {} steps", plan.getStepCount());
        } catch (Exception e) {
            log.error("[DeepResearchAgent] Plan creation failed: {}", e.getMessage());
            state.setPhase(ResearchState.ReActPhase.ERROR);
            state.setShouldContinue(false);
        }
    }

    private void executeReActLoop(ResearchState state) {
        while (state.isShouldContinue() && !state.hasExceededMaxIterations()) {
            ResearchStep currentStep = state.getPlan().getCurrentStep();
            if (currentStep == null || state.getPlan().isComplete()) {
                break;
            }

            if (currentStep.getType() == ResearchStep.StepType.SYNTHESIZE) {
                currentStep.markInProgress();
                break;
            }

            executeThinkPhase(state, currentStep);
            executeSearchPhase(state, currentStep);
            executeObservePhase(state, currentStep);
        }

        executeSynthesizePhase(state);
    }

    private void executeThinkPhase(ResearchState state, ResearchStep step) {
        state.setPhase(ResearchState.ReActPhase.THINK);
        step.markInProgress();
        state.setCurrentThought(step.getDescription());
        state.setCurrentAction(step.getSearchQuery());
        log.info("[DeepResearchAgent] THINK - Iteration {}: {} - {}",
                state.getIterationCount() + 1, step.getType(), step.getDescription());
    }

    private void executeSearchPhase(ResearchState state, ResearchStep step) {
        state.setPhase(ResearchState.ReActPhase.ACT);
        try {
            String searchQuery = step.getSearchQuery();
            if (searchQuery == null || searchQuery.isEmpty()) {
                searchQuery = state.getQuery();
            }

            List<SearchResult> results = searchExecutor.search(searchQuery);
            for (SearchResult result : results) {
                state.addSearchResult(result);
            }
            log.info("[DeepResearchAgent] ACT (Search) - Query: {}, Results: {}", searchQuery, results.size());
        } catch (Exception e) {
            log.error("[DeepResearchAgent] Search failed: {}", e.getMessage());
            state.setCurrentObservation("Search failed: " + e.getMessage());
        }
    }

    private void executeObservePhase(ResearchState state, ResearchStep step) {
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

        log.info("[DeepResearchAgent] OBSERVE - Progress: {}/{}",
                state.getPlan().getCompletedStepCount(), state.getPlan().getStepCount());
    }

    private void executeSynthesizePhase(ResearchState state) {
        log.info("[DeepResearchAgent] Phase: SYNTHESIZE");
        state.setPhase(ResearchState.ReActPhase.SYNTHESIZE);

        try {
            String report = synthesizeExecutor.synthesize(
                    state.getQuery(),
                    state.getAccumulatedObservations(),
                    state.getCycleHistory()
            );
            state.setFinalReport(report);
            state.setPhase(ResearchState.ReActPhase.COMPLETE);
            log.info("[DeepResearchAgent] Report generated: {} chars", report.length());
        } catch (Exception e) {
            log.error("[DeepResearchAgent] Synthesis failed: {}", e.getMessage());
            state.setPhase(ResearchState.ReActPhase.ERROR);
        }
    }

    public String researchAsText(String query) {
        ResearchResult result = research(query);
        if (result.isSuccess()) {
            return result.getReport();
        } else {
            return "Research failed: " + result.getErrorMessage();
        }
    }
}
