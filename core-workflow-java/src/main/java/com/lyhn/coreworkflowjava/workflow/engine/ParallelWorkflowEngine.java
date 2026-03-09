package com.lyhn.coreworkflowjava.workflow.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Parallel Workflow execution engine
 * Executes workflow nodes in parallel where possible
 * 并行工作流引擎 把节点执行任务化然后交给线程池去并发处理
 */
@Slf4j
@Component
public class ParallelWorkflowEngine {

}