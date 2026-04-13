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
public class SearchResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String title;

    private String url;

    private String snippet;

    private String source;

    @Builder.Default
    private double relevanceScore = 0.0;
}
