package com.lyhn.coreworkflowjava.workflow.engine.domain.callbacks;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Delta {
    /**
     * Role of the entity generating the content.
     */
    private String role = "assistant";
    
    /**
     * Main content of the response.
     */
    private String content = "";
    
    /**
     * Reasoning or intermediate content for explainability.
     */
    @JsonProperty("reasoning_content")
    private String reasoningContent = "";
    
    public Delta() {
    }
    
    public Delta(String role, String content, String reasoningContent) {
        this.role = role;
        this.content = content;
        this.reasoningContent = reasoningContent;
    }
    
    // Getters and setters
    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getReasoningContent() {
        return reasoningContent;
    }
    
    public void setReasoningContent(String reasoningContent) {
        this.reasoningContent = reasoningContent;
    }
}