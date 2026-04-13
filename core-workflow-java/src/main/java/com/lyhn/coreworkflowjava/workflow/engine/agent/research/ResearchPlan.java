package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    private String query;

    private List<ResearchStep> steps;

    @Builder.Default
    private int currentStepIndex = 0;

    @Builder.Default
    private PlanStatus status = PlanStatus.PENDING;

    public enum PlanStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    public ResearchStep getCurrentStep() {
        if (steps == null || currentStepIndex >= steps.size()) {
            return null;
        }
        return steps.get(currentStepIndex);
    }

    public void advanceToNextStep() {
        if (steps != null && currentStepIndex < steps.size() - 1) {
            currentStepIndex++;
        } else {
            status = PlanStatus.COMPLETED;
        }
    }

    public boolean isComplete() {
        return status == PlanStatus.COMPLETED ||
                (steps != null && currentStepIndex >= steps.size());
    }

    public int getStepCount() {
        return steps != null ? steps.size() : 0;
    }

    public int getCompletedStepCount() {
        return currentStepIndex;
    }
}
