package com.lyhn.coreworkflowjava.workflow.engine.domain;
import com.fasterxml.jackson.annotation.JsonProperty;

import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Edge;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Node;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDSL {
    /**
     * Workflow ID
     */
    private String flowId;

    /**
     * Workflow UUID
     */
    private String uuid;

    /**
     * List of nodes in the workflow
     */
    @JsonProperty("nodes")
    private List<Node> nodes = new ArrayList<>();

    /**
     * List of edges connecting nodes
     */
    @JsonProperty("edges")
    private List<Edge> edges = new ArrayList<>();


}