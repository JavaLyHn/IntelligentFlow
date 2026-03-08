package com.lyhn.coreworkflowjava.workflow.controller.vo;

import lombok.Data;

import java.util.Map;

@Data
public class SaveComparisonRequest {
    private String flowId;
    private Map<String, Object> data;
    private String version;
}