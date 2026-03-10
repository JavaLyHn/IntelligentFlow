package com.lyhn.coreworkflowjava.workflow.controller;
import com.lyhn.coreworkflowjava.workflow.controller.vo.NodeDebugRespVo;
import com.lyhn.coreworkflowjava.workflow.controller.vo.RspVo;
import com.lyhn.coreworkflowjava.workflow.engine.VariablePool;
import com.lyhn.coreworkflowjava.workflow.engine.WorkflowEngine;
import com.lyhn.coreworkflowjava.workflow.engine.WorkflowEngineNodeDebug;
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
 * 调试和测试接口
 */
@Slf4j
@RestController
@RequestMapping({"/workflow/v1"})
public class WorkflowDebugController {
    private final WorkflowService workflowService;
    private final WorkflowEngineNodeDebug workflowEngineNodeDebug;
    private final WorkflowEngine workflowEngine;

    public WorkflowDebugController(WorkflowService workflowService, WorkflowEngineNodeDebug workflowEngineNodeDebug, WorkflowEngine workflowEngine) {
        this.workflowService = workflowService;
        this.workflowEngineNodeDebug = workflowEngineNodeDebug;
        this.workflowEngine = workflowEngine;
    }

    /**
     * Execute workflow with SSE streaming
     * <p>
     * Endpoint: POST /workflow/v1/debug/chat/completions
     * Request body:
     * {"stream":true,"debug":true,"parameters":{"AGENT_USER_INPUT":"给我说一个三国的笑话吧"},"uid":"admin","flow_id":"7399634992520073218"}
     * <p>
     * Response: SSE stream with events:
     * - node_start: Node execution started
     * - node_output: Node produced output
     * - node_end: Node execution completed
     * - workflow_complete: Workflow finished
     * - error: Error occurred
     */
    @PostMapping(value = {"/debug/chat/completions"}, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeWorkflow(@RequestBody WorkflowDebugRequest request) {
        log.info("Workflow execution request: flowId={}, inputs={}", request.getFlowId(), request);

        SseEmitter emitter = new SseEmitter();

        AsyncUtil.execute(() -> {
            try {
                WorkflowDSL workflowDSL = workflowService.getWorkflowDSL(request.getFlowId());
                workflowDSL.setUuid(request.uuid);

                StreamCallback callback = new SseStreamCallback(emitter);

                workflowEngine.execute(workflowDSL, new VariablePool(), request.getParameters(), callback);

            } catch (Exception e) {
                log.error("Workflow execution failed: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            } finally {
                // 判断 emitter 是否已经完结，如果没有，则主动完结
                emitter.complete();
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


    /**
     * Debug a node in the workflow
     * <p>
     * Endpoint: POST /workflow/v1/node/debug
     * Request body: {"id":"7399634992520073218","name":"Test Workflow","description":"Test Description","data":{"nodes":[...],"edges":[...]}}
     * <p>
     * Response: JSON response with debug execution result
     */
    @PostMapping(value = {"/node/debug"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public RspVo<NodeDebugRespVo> nodeDebug(@RequestBody NodeDebugRequest request) {
        log.info("Node debug request: id={}", request.id());

        try {
            NodeDebugRespVo result = workflowEngineNodeDebug.nodeDebug(request.data(), request.id());
            log.info("Node debug completed successfully: nodeId={}", result.getNodeId());
            return RspVo.success(result, RspVo.generateSid());
        } catch (Exception e) {
            log.error("Node debug failed: {}", e.getMessage(), e);
            return RspVo.error(-1, e.getMessage(), RspVo.generateSid());
        }
    }
    /**
     * Workflow execution request
     */
    @Data
    public static class WorkflowDebugRequest {

        @com.fasterxml.jackson.annotation.JsonProperty("flow_id")
        private String flowId;

        /**
         * 是否流式返回
         */
        @com.fasterxml.jackson.annotation.JsonProperty("stream")
        private Boolean stream;

        @com.fasterxml.jackson.annotation.JsonProperty("debug")
        private Boolean debug;

        @com.fasterxml.jackson.annotation.JsonProperty("uid")
        private String uuid;

        /**
         * 输入参数
         */
        @com.fasterxml.jackson.annotation.JsonProperty("parameters")
        private Map<String, Object> parameters;
    }

    public record NodeDebugRequest(String id, String name, String description, WorkflowDSL data) {
    }
}