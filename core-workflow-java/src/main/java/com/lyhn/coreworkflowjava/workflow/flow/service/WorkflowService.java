package com.lyhn.coreworkflowjava.workflow.flow.service;

import com.lyhn.coreworkflowjava.workflow.flow.mapper.WorkflowMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WorkflowService {
    private final WorkflowMapper workflowMapper;

    public WorkflowService(WorkflowMapper workflowMapper) {
        this.workflowMapper = workflowMapper;
    }


}