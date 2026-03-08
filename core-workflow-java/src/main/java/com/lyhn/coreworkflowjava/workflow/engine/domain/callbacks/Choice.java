package com.lyhn.coreworkflowjava.workflow.engine.domain.callbacks;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Choice {
    /**
     * Delta content for this choice.
     */
    private Delta delta;
    
    /**
     * Index of this choice in the response.
     */
    private Integer index = null;
    
    /**
     * Reason for completion: 'stop' for normal completion, 'interrupt' for interruption, null for ongoing.
     */
    @JsonProperty("finish_reason")
    private String finishReason = null;
    
    public Choice() {
    }
    
    public Choice(Delta delta, Integer index, String finishReason) {
        this.delta = delta;
        this.index = index;
        this.finishReason = finishReason;
    }
    
    // Getters and setters
    public Delta getDelta() {
        return delta;
    }
    
    public void setDelta(Delta delta) {
        this.delta = delta;
    }
    
    public Integer getIndex() {
        return index;
    }
    
    public void setIndex(Integer index) {
        this.index = index;
    }
    
    public String getFinishReason() {
        return finishReason;
    }
    
    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }
}