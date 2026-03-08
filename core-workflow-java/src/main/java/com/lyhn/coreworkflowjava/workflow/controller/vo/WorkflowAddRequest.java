package com.lyhn.coreworkflowjava.workflow.controller.vo;

import lombok.Data;

import java.util.Map;

@Data
public class WorkflowAddRequest {
    private Long groupId;
    private String name;
    private Map<String, Object> data;
    private String description;
    private String appId;
    private Integer source;
    private String version;
    private Integer tag;
}