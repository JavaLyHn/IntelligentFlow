package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanningResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String planId;

    private String query;

    private ResearchPlan plan;

    @Builder.Default
    private long elapsedMs = 0;

    @Builder.Default
    private boolean success = true;

    private String errorMessage;

    public static PlanningResult success(String planId, String query, ResearchPlan plan, long elapsedMs) {
        return PlanningResult.builder()
                .planId(planId)
                .query(query)
                .plan(plan)
                .elapsedMs(elapsedMs)
                .success(true)
                .build();
    }

    public static PlanningResult failure(String query, String errorMessage, long elapsedMs) {
        return PlanningResult.builder()
                .query(query)
                .errorMessage(errorMessage)
                .elapsedMs(elapsedMs)
                .success(false)
                .build();
    }
}
