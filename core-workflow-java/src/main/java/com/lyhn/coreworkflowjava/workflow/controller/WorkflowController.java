package com.lyhn.coreworkflowjava.workflow.controller;
import com.lyhn.coreworkflowjava.workflow.engine.VariablePool;
import com.lyhn.coreworkflowjava.workflow.engine.WorkflowEngine;
import com.lyhn.coreworkflowjava.workflow.engine.domain.WorkflowDSL;
import com.lyhn.coreworkflowjava.workflow.engine.node.StreamCallback;
import com.lyhn.coreworkflowjava.workflow.engine.node.callback.SseStreamCallback;
import com.lyhn.coreworkflowjava.workflow.engine.util.AsyncUtil;
import com.lyhn.coreworkflowjava.workflow.flow.service.WorkflowService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
/**
 * 标准的工作流执行接口
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {
    private final WorkflowService workflowService;
    private final WorkflowEngine workflowEngine;

    public WorkflowController(WorkflowService workflowService, WorkflowEngine workflowEngine) {
        this.workflowService = workflowService;
        this.workflowEngine = workflowEngine;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeWorkflow(@RequestBody WorkflowRequest request) {
        log.info("Workflow execution request: flowId={}, inputs={}", request.getFlowId(), request.getInputs());

        // 在10min内没有发送任何事件 连接自动断开
        SseEmitter emitter = new SseEmitter(600_000L);

        AsyncUtil.execute(() -> {
            try{
                WorkflowDSL workflowDSL = workflowService.getWorkflowDSL(request.getFlowId());
                workflowDSL.setUuid(request.getChatId());

                StreamCallback callback = new SseStreamCallback(emitter);
                workflowEngine.execute(workflowDSL, new VariablePool(), request.getInputs(), callback);

                emitter.complete();
            } catch (Exception e) {
                log.error("Workflow execution failed: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        emitter.onTimeout(() -> {
            log.warn("Workflow execution timeout");
            emitter.complete();
        });

        emitter.onError(e -> {
            log.error("SSE error: {}", e.getMessage(), e);
            emitter.completeWithError(e);
        });

        return emitter;
    }

    @Data
    public static class WorkflowRequest {

        @com.fasterxml.jackson.annotation.JsonProperty("flow_id")
        private String flowId;

        @com.fasterxml.jackson.annotation.JsonProperty("inputs")
        private Map<String, Object> inputs;

        @com.fasterxml.jackson.annotation.JsonProperty("chatId")
        private String chatId;

        @com.fasterxml.jackson.annotation.JsonProperty("regen")
        private Boolean regen;
    }
}