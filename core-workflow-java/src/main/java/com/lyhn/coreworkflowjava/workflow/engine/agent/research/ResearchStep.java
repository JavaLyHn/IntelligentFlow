package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchStep implements Serializable {

    private static final long serialVersionUID = 1L;

    private StepType type;

    private String description;

    private String searchQuery;

    private Map<String, Object> parameters;

    @Builder.Default
    private StepStatus status = StepStatus.PENDING;

    private String result;

    public enum StepType {
        PLAN,
        SEARCH,
        SYNTHESIZE
    }

    public enum StepStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        SKIPPED
    }

    public void markInProgress() {
        this.status = StepStatus.IN_PROGRESS;
    }

    public void markCompleted(String result) {
        this.status = StepStatus.COMPLETED;
        this.result = result;
    }

    public void markFailed(String error) {
        this.status = StepStatus.FAILED;
        this.result = error;
    }
}
