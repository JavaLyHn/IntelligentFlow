package com.lyhn.coreworkflowjava.workflow.engine.domain.callbacks;

// 表示工作流执行步骤的核心数据结构
public class WorkflowStep {
    /**
     * Information about the node being executed in this step.
     */
    private NodeInfo node = null;
    
    /**
     * Sequence number of the workflow step.
     */
    private int seq = 0;
    
    /**
     * Progress statistics for workflow execution (0.0 to 1.0).
     */
    private double progress = 0.0;
    
    public WorkflowStep() {
    }
    
    public WorkflowStep(NodeInfo node, int seq, double progress) {
        this.node = node;
        this.seq = seq;
        this.progress = progress;
    }
    
    // Getters and setters
    public NodeInfo getNode() {
        return node;
    }
    
    public void setNode(NodeInfo node) {
        this.node = node;
    }
    
    public int getSeq() {
        return seq;
    }
    
    public void setSeq(int seq) {
        this.seq = seq;
    }
    
    public double getProgress() {
        return progress;
    }
    
    public void setProgress(double progress) {
        this.progress = progress;
    }
}