package com.lyhn.coreworkflowjava.workflow.engine.agent.collaboration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiAgentResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String task;

    private String mode;

    private String finalOutput;

    @Builder.Default
    private Map<String, Object> agentResults = new HashMap<>();

    @Builder.Default
    private int totalIterations = 0;

    @Builder.Default
    private long elapsedMs = 0;

    @Builder.Default
    private boolean success = true;

    private String errorMessage;

    public static MultiAgentResult success(String task, String mode, String output,
                                            Map<String, Object> agentResults,
                                            int iterations, long elapsedMs) {
        return MultiAgentResult.builder()
                .task(task)
                .mode(mode)
                .finalOutput(output)
                .agentResults(agentResults)
                .totalIterations(iterations)
                .elapsedMs(elapsedMs)
                .success(true)
                .build();
    }

    public static MultiAgentResult failure(String task, String mode, String error, long elapsedMs) {
        return MultiAgentResult.builder()
                .task(task)
                .mode(mode)
                .errorMessage(error)
                .elapsedMs(elapsedMs)
                .success(false)
                .build();
    }
}
