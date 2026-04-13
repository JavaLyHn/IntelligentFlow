package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResearchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String query;

    private String report;

    private List<SearchResult> sources;

    @Builder.Default
    private int totalIterations = 0;

    @Builder.Default
    private long elapsedMs = 0;

    @Builder.Default
    private boolean success = true;

    private String errorMessage;

    public static ResearchResult success(String query, String report, List<SearchResult> sources,
                                         int iterations, long elapsedMs) {
        return ResearchResult.builder()
                .query(query)
                .report(report)
                .sources(sources)
                .totalIterations(iterations)
                .elapsedMs(elapsedMs)
                .success(true)
                .build();
    }

    public static ResearchResult failure(String query, String errorMessage, int iterations, long elapsedMs) {
        return ResearchResult.builder()
                .query(query)
                .errorMessage(errorMessage)
                .totalIterations(iterations)
                .elapsedMs(elapsedMs)
                .success(false)
                .build();
    }
}
