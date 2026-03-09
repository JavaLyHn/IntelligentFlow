package com.lyhn.coreworkflowjava.workflow.engine;

import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.node.NodeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 负责调度和执行链路
 */
@Slf4j
@Component
public class WorkflowEngine {
    private final Map<NodeTypeEnum, NodeExecutor> nodeExecutors;
}