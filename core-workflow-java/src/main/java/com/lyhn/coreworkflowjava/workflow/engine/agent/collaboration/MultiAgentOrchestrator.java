package com.lyhn.coreworkflowjava.workflow.engine.agent.collaboration;

import com.lyhn.coreworkflowjava.workflow.engine.agent.research.SearchExecutor;
import com.lyhn.coreworkflowjava.workflow.engine.agent.research.SynthesizeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;

import java.util.*;

@Slf4j
public class MultiAgentOrchestrator {

    private final CollaborationGraph collaborationGraph;
    private final SearchExecutor searchExecutor;
    private final SynthesizeExecutor synthesizeExecutor;

    public MultiAgentOrchestrator(SearchExecutor searchExecutor,
                                  SynthesizeExecutor synthesizeExecutor) {
        this.searchExecutor = searchExecutor;
        this.synthesizeExecutor = synthesizeExecutor;
        this.collaborationGraph = new CollaborationGraph(searchExecutor, synthesizeExecutor);
    }

    public MultiAgentResult orchestrate(String mode, List<AgentDefinition> agents,
                                         String task, int maxIterations) {
        log.info("[MultiAgentOrchestrator] Starting orchestration: mode={}, agents={}, task={}",
                mode, agents.size(), task);

        long startTime = System.currentTimeMillis();

        try {
            CompiledGraph<CollaborationState> graph = collaborationGraph.buildGraph(
                    mode, agents, task, maxIterations);

            Map<String, Object> initialState = new HashMap<>();
            initialState.put(CollaborationState.TASK, task);
            initialState.put(CollaborationState.CURRENT_AGENT, "");
            initialState.put(CollaborationState.ITERATION, 0);
            initialState.put(CollaborationState.MAX_ITERATIONS, maxIterations);
            initialState.put(CollaborationState.AGENT_RESULTS, new HashMap<String, Object>());
            initialState.put(CollaborationState.CONTEXT, new HashMap<String, Object>());
            initialState.put(CollaborationState.FINAL_OUTPUT, "");

            String finalOutput = "";
            Map<String, Object> finalAgentResults = new HashMap<>();
            int finalIteration = 0;

            for (var nodeOutput : graph.stream(initialState)) {
                log.debug("[MultiAgentOrchestrator] Node output: {}", nodeOutput);
                if (nodeOutput != null && nodeOutput.state() != null) {
                    CollaborationState state = nodeOutput.state();
                    if (state.getFinalOutput() != null && !state.getFinalOutput().isEmpty()) {
                        finalOutput = state.getFinalOutput();
                    }
                    if (state.getAgentResults() != null && !state.getAgentResults().isEmpty()) {
                        finalAgentResults.putAll(state.getAgentResults());
                    }
                    finalIteration = state.getIteration();
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;

            log.info("[MultiAgentOrchestrator] Orchestration completed: mode={}, iterations={}, elapsed={}ms",
                    mode, finalIteration, elapsed);

            return MultiAgentResult.builder()
                    .task(task)
                    .mode(mode)
                    .finalOutput(finalOutput)
                    .agentResults(finalAgentResults)
                    .totalIterations(finalIteration)
                    .elapsedMs(elapsed)
                    .success(true)
                    .build();

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[MultiAgentOrchestrator] Orchestration failed: {}", e.getMessage(), e);
            return MultiAgentResult.builder()
                    .task(task)
                    .mode(mode)
                    .elapsedMs(elapsed)
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
}
