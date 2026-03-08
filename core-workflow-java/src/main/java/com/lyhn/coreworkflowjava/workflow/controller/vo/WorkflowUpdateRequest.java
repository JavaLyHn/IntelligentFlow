package com.lyhn.coreworkflowjava.workflow.controller.vo;

import lombok.Data;

import java.util.Map;

@Data
public class WorkflowUpdateRequest {
    private String name;
    private String description;
    private Map<String, Object> data;
    private String appId;
}