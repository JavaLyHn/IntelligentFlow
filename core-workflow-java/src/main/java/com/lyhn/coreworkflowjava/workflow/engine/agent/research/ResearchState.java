package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchState implements Serializable {

    private static final long serialVersionUID = 1L;

    private String query;

    private ResearchPlan plan;

    @Builder.Default
    private ReActPhase phase = ReActPhase.INIT;

    @Builder.Default
    private int iterationCount = 0;

    @Builder.Default
    private int maxIterations = 10;

    private String currentThought;

    private String currentAction;

    private String currentObservation;

    @Builder.Default
    private List<ReActCycle> cycles = new ArrayList<>();

    @Builder.Default
    private List<SearchResult> searchResults = new ArrayList<>();

    private String finalReport;

    @Builder.Default
    private boolean shouldContinue = true;

    public enum ReActPhase {
        INIT,
        PLAN,
        THINK,
        ACT,
        OBSERVE,
        SYNTHESIZE,
        COMPLETE,
        ERROR
    }

    public void addCycle(ReActCycle cycle) {
        if (cycles == null) {
            cycles = new ArrayList<>();
        }
        cycles.add(cycle);
        iterationCount = cycles.size();
    }

    public void addSearchResult(SearchResult result) {
        if (searchResults == null) {
            searchResults = new ArrayList<>();
        }
        searchResults.add(result);
    }

    public boolean hasExceededMaxIterations() {
        return iterationCount >= maxIterations;
    }

    public String getAccumulatedObservations() {
        if (searchResults == null || searchResults.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < searchResults.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(searchResults.get(i).getTitle()).append("\n");
            sb.append(searchResults.get(i).getSnippet()).append("\n\n");
        }
        return sb.toString();
    }

    public String getCycleHistory() {
        if (cycles == null || cycles.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cycles.size(); i++) {
            ReActCycle cycle = cycles.get(i);
            sb.append("--- Iteration ").append(i + 1).append(" ---\n");
            sb.append("Thought: ").append(cycle.thought()).append("\n");
            sb.append("Action: ").append(cycle.action()).append("\n");
            sb.append("Observation: ").append(cycle.observation()).append("\n\n");
        }
        return sb.toString();
    }
}
