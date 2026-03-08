package com.lyhn.coreworkflowjava.workflow.controller.vo;

import lombok.Data;

@Data
public class DeleteComparisonRequest {
    private String flowId;
    private String version;
}