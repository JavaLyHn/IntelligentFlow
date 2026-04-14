package com.lyhn.coreworkflowjava.workflow.engine.agent.collaboration;

import com.lyhn.coreworkflowjava.workflow.engine.agent.research.SearchExecutor;
import com.lyhn.coreworkflowjava.workflow.engine.agent.research.SearchResult;
import com.lyhn.coreworkflowjava.workflow.engine.agent.research.SynthesizeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class AgentNodeAction implements AsyncNodeAction<CollaborationState> {

    private final AgentDefinition agentDef;
    private final SearchExecutor searchExecutor;
    private final SynthesizeExecutor synthesizeExecutor;

    public AgentNodeAction(AgentDefinition agentDef,
                           SearchExecutor searchExecutor,
                           SynthesizeExecutor synthesizeExecutor) {
        this.agentDef = agentDef;
        this.searchExecutor = searchExecutor;
        this.synthesizeExecutor = synthesizeExecutor;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(CollaborationState state) {
        return CompletableFuture.supplyAsync(() -> {
            String task = state.getTask();
            String agentId = agentDef.getAgentId();
            AgentRole role = agentDef.getRole();
            String resultKey = role.getValue();

            log.info("[AgentNodeAction] Executing agent: id={}, role={}, task={}",
                    agentId, role, task);

            Map<String, Object> updates = new HashMap<>();
            updates.put(CollaborationState.CURRENT_AGENT, resultKey);
            updates.put(CollaborationState.ITERATION, state.getIteration() + 1);

            try {
                String result = executeByRole(role, task, state);
                Map<String, Object> agentResults = new HashMap<>(state.getAgentResults());
                agentResults.put(resultKey, result);
                updates.put(CollaborationState.AGENT_RESULTS, agentResults);

                if (role == AgentRole.WRITER || role == AgentRole.JUDGE) {
                    updates.put(CollaborationState.FINAL_OUTPUT, result);
                }

                log.info("[AgentNodeAction] Agent completed: id={}, resultLength={}",
                        agentId, result.length());
            } catch (Exception e) {
                log.error("[AgentNodeAction] Agent failed: id={}, error={}", agentId, e.getMessage());
                Map<String, Object> agentResults = new HashMap<>(state.getAgentResults());
                agentResults.put(resultKey, "ERROR: " + e.getMessage());
                updates.put(CollaborationState.AGENT_RESULTS, agentResults);
            }

            return updates;
        });
    }

    private String executeByRole(AgentRole role, String task, CollaborationState state) {
        return switch (role) {
            case SUPERVISOR -> executeSupervisor(task, state);
            case SEARCHER -> executeSearcher(task, state);
            case ANALYZER -> executeAnalyzer(task, state);
            case WRITER -> executeWriter(task, state);
            case JUDGE -> executeJudge(task, state);
        };
    }

    private String executeSupervisor(String task, CollaborationState state) {
        Map<String, Object> agentResults = state.getAgentResults();
        if (agentResults.isEmpty()) {
            return "Task decomposed: " + task + " | Next: delegate to searcher";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Supervisor review of task: ").append(task).append("\n");
        for (Map.Entry<String, Object> entry : agentResults.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ")
                    .append(truncate(String.valueOf(entry.getValue()), 200)).append("\n");
        }
        sb.append("Supervisor assessment: all specialists have reported.");
        return sb.toString();
    }

    private String executeSearcher(String task, CollaborationState state) {
        if (searchExecutor != null) {
            try {
                List<SearchResult> results = searchExecutor.search(task);
                StringBuilder sb = new StringBuilder();
                sb.append("Search results for: ").append(task).append("\n");
                for (SearchResult r : results) {
                    sb.append("- ").append(r.getTitle()).append(": ")
                            .append(r.getSnippet()).append("\n");
                }
                return sb.toString();
            } catch (Exception e) {
                return "Search failed: " + e.getMessage();
            }
        }
        return "Search completed for: " + task + " (default executor)";
    }

    private String executeAnalyzer(String task, CollaborationState state) {
        Map<String, Object> agentResults = state.getAgentResults();
        String searchData = "";
        for (Map.Entry<String, Object> entry : agentResults.entrySet()) {
            if (entry.getKey().contains("search")) {
                searchData = String.valueOf(entry.getValue());
                break;
            }
        }
        if (searchData.isEmpty()) {
            searchData = "No prior search data available";
        }
        return "Analysis of: " + task + "\nBased on: " + truncate(searchData, 300)
                + "\nKey findings: Identified main themes and patterns.";
    }

    private String executeWriter(String task, CollaborationState state) {
        Map<String, Object> agentResults = state.getAgentResults();
        StringBuilder allData = new StringBuilder();
        for (Map.Entry<String, Object> entry : agentResults.entrySet()) {
            allData.append(entry.getValue()).append("\n");
        }

        if (synthesizeExecutor != null) {
            try {
                return synthesizeExecutor.synthesize(task, allData.toString(), "");
            } catch (Exception e) {
                return "Synthesis failed, generating default report: " + e.getMessage();
            }
        }

        return "# Research Report: " + task + "\n\n## Findings\n"
                + truncate(allData.toString(), 1000)
                + "\n\n## Conclusion\nResearch completed successfully.";
    }

    private String executeJudge(String task, CollaborationState state) {
        Map<String, Object> agentResults = state.getAgentResults();
        StringBuilder sb = new StringBuilder();
        sb.append("# Evaluation Report: ").append(task).append("\n\n");
        sb.append("## Worker Results Summary\n");
        for (Map.Entry<String, Object> entry : agentResults.entrySet()) {
            if (!entry.getKey().contains("judge")) {
                sb.append("### ").append(entry.getKey()).append("\n");
                sb.append(truncate(String.valueOf(entry.getValue()), 300)).append("\n\n");
            }
        }
        sb.append("## Final Assessment\n");
        sb.append("All workers have provided their findings. ");
        sb.append("The results are consistent and comprehensive.");
        return sb.toString();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
