package com.lyhn.coreworkflowjava.workflow.engine.domain.callbacks;

import com.lyhn.coreworkflowjava.workflow.engine.constants.ChatStatusEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.EndNodeOutputModeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeRunResult;
import com.lyhn.coreworkflowjava.workflow.exception.ErrorCode;
import com.lyhn.coreworkflowjava.workflow.exception.NodeCustomException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ChatCallBacks {
    private String sid;
    private GenerateUsage generateUsage = new GenerateUsage();
    @Getter
    private Queue<LLMGenerate> streamQueue;
    private Map<String, Long> nodeExecuteStartTime = new ConcurrentHashMap<>();
    private EndNodeOutputModeEnum endNodeOutputMode;
    private Set<String> supportStreamNodeIdSet;
    @Getter
    private Queue<ChatCallBackStreamResult> orderStreamResultQ;

    public ChatCallBacks(String sid, Queue<LLMGenerate> streamQueue,
                         EndNodeOutputModeEnum endNodeOutputMode, Set<String> supportStreamNodeIds,
                         Queue<ChatCallBackStreamResult> needOrderStreamResultQ) {
        this.sid = sid;
        this.streamQueue = streamQueue;
        this.endNodeOutputMode = endNodeOutputMode;
        this.supportStreamNodeIdSet = supportStreamNodeIds;
        this.orderStreamResultQ = needOrderStreamResultQ;
    }

    private double getNodeProgress(String currentExecuteNodeId) {
        // 简化实现，不依赖Chains
        return 0.0;
    }

    public void onSparkflowStart() {
        LLMGenerate resp = LLMGenerate.workflowStart(this.sid);
        this.putFrameIntoQueue("WorkflowStart", resp, null);
    }

    public void onSparkflowEnd(NodeRunResult message) {
        int code = ErrorCode.Success.getCode();
        String msg = ErrorCode.Success.getMsg();

        if (message.getError() != null) {
            code = message.getError().getCode();
            msg = message.getError().getMessage();
        }

        LLMGenerate resp = LLMGenerate.workflowEnd(
                this.sid,
                this.generateUsage,
                code,
                msg
        );
        this.putFrameIntoQueue("WorkflowEnd", resp, null);
    }

    public void onNodeStart(int code, String nodeId, String aliasName) {
        this.nodeExecuteStartTime.put(nodeId, System.currentTimeMillis());
        LLMGenerate resp = LLMGenerate.nodeStart(
                this.sid,
                nodeId,
                aliasName,
                this.getNodeProgress(nodeId),
                code,
                "Success"
        );
        this.putFrameIntoQueue(nodeId, resp);
    }

    public void onNodeProcess(int code, String nodeId, String aliasName,
                              String message, String reasoningContent) {
        Map<String, Object> ext = null;
        if (nodeId.split(":")[0].equals(NodeTypeEnum.END.getValue())) {
            ext = new HashMap<>();
            ext.put("answer_mode", this.endNodeOutputMode.getValue());
        }

        String content = (code == 0) ? message : "";  // If error occurs, content is empty
        if (nodeId.split(":")[0].equals(NodeTypeEnum.END.getValue())) {
            if (this.endNodeOutputMode == EndNodeOutputModeEnum.VARIABLE_MODE) {
                content = "";
            }
        }

        long startTime = this.nodeExecuteStartTime.getOrDefault(nodeId, System.currentTimeMillis());
        double executedTime = (System.currentTimeMillis() - startTime) / 1000.0;

        LLMGenerate resp = LLMGenerate.nodeProcess(
                this.sid,
                nodeId,
                aliasName,
                executedTime,
                ext,
                this.getNodeProgress(nodeId),
                content,
                reasoningContent,
                code,
                (code == 0) ? "Success" : message
        );
        this.putFrameIntoQueue(nodeId, resp);
    }

    public void onNodeInterrupt(String eventId, Map<String, Object> value,
                                String nodeId, String aliasName, int code,
                                String finishReason, boolean needReply) {
        long startTime = this.nodeExecuteStartTime.getOrDefault(nodeId, System.currentTimeMillis());
        double executedTime = (System.currentTimeMillis() - startTime) / 1000.0;

        LLMGenerate resp = LLMGenerate.nodeInterrupt(
                this.sid,
                eventId,
                value,
                nodeId,
                aliasName,
                executedTime,
                null,
                this.getNodeProgress(nodeId),
                finishReason,
                needReply,
                code,
                "Success"
        );
        this.putFrameIntoQueue(nodeId, resp);
    }

    public void onNodeEnd(String nodeId, String aliasName,
                          NodeRunResult message, NodeCustomException error) {
        String nodeType = nodeId.split(":")[0];
        Map<String, Object> ext = new HashMap<>();

        if (error != null) {
            this.onNodeEndError(nodeId, aliasName, error);
            return;
        }

        if (message == null) {
            NodeCustomException defaultError = new NodeCustomException(
                    ErrorCode.NODE_RUN_ERROR,
                    "Node run error, please check the node configuration"
            );
            this.onNodeEndError(nodeId, aliasName, defaultError);
            return;
        }

        if (message.getError() != null) {
            this.onNodeEndError(nodeId, aliasName, message.getError());
            return;
        }

        if (message.getTokenCost() != null) {
            this.generateUsage.add(message.getTokenCost());
        }

        if (nodeType.equals(NodeTypeEnum.LLM.getValue()) ||
                nodeType.equals(NodeTypeEnum.DECISION_MAKING.getValue())) {
            if (message.getRawOutput() != null && !message.getRawOutput().isEmpty()) {
                ext.put("raw_output", message.getRawOutput());
            }
            if (nodeType.equals(NodeTypeEnum.END.getValue())) {
                ext.put("answer_mode", this.endNodeOutputMode.getValue());
            }
        }

        String content = message.getNodeAnswerContent();
        if (nodeType.equals(NodeTypeEnum.END.getValue()) &&
                this.endNodeOutputMode == EndNodeOutputModeEnum.DIRECT_MODE) {
            // In Java, we would need to convert the outputs map to JSON string
            // This is a simplified representation
            content = message.getOutputs().toString();
            long startTime = this.nodeExecuteStartTime.getOrDefault(nodeId, System.currentTimeMillis());
            double executedTime = (System.currentTimeMillis() - startTime) / 1000.0;

            NodeInfo nodeInfo = new NodeInfo();
            nodeInfo.setId(nodeId);
            nodeInfo.setAliasName("Node:" + aliasName);
            nodeInfo.setFinishReason(ChatStatusEnum.STOP.getStatus());
            nodeInfo.setInputs(message.getInputs());
            nodeInfo.setOutputs(message.getOutputs());
            nodeInfo.setErrorOutputs(message.getErrorOutputs());
            nodeInfo.setExt(ext);
            nodeInfo.setExecutedTime(executedTime);
            nodeInfo.setUsage(message.getTokenCost());

            WorkflowStep workflowStep = new WorkflowStep();
            workflowStep.setNode(nodeInfo);
            workflowStep.setProgress(this.getNodeProgress(nodeId));

            Delta delta = new Delta();
            delta.setContent(content);
            delta.setReasoningContent(message.getNodeAnswerReasoningContent());

            Choice choice = new Choice();
            choice.setDelta(delta);

            LLMGenerate resp = new LLMGenerate();
            resp.setId(this.sid);
            resp.setWorkflowStep(workflowStep);
            resp.getChoices().add(choice);

            this.putFrameIntoQueue(nodeId, resp, ChatStatusEnum.STOP.getStatus());
        }
    }

    private void onNodeEndError(String nodeId, String aliasName, NodeCustomException error) {
        String nodeType = nodeId.split(":")[0];

        long startTime = this.nodeExecuteStartTime.getOrDefault(nodeId, System.currentTimeMillis());
        double executedTime = (System.currentTimeMillis() - startTime) / 1000.0;

        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setId(nodeId);
        nodeInfo.setAliasName(aliasName);
        nodeInfo.setFinishReason("stop");
        nodeInfo.setExecutedTime(executedTime);

        if (nodeType.equals(NodeTypeEnum.LLM.getValue()) ||
                nodeType.equals(NodeTypeEnum.DECISION_MAKING.getValue())) {
            nodeInfo.setUsage(new GenerateUsage());
        }

        WorkflowStep workflowStep = new WorkflowStep();
        workflowStep.setNode(nodeInfo);
        workflowStep.setProgress(this.getNodeProgress(nodeId));

        Delta delta = new Delta();

        Choice choice = new Choice();
        choice.setDelta(delta);

        LLMGenerate resp = new LLMGenerate();
        resp.setCode(error.getCode());
        resp.setMessage(error.getMessage());
        resp.setId(this.sid);
        resp.setWorkflowStep(workflowStep);
        resp.getChoices().add(choice);

        this.putFrameIntoQueue(nodeId, resp, "stop");
    }

    private void putFrameIntoQueue(String nodeId, LLMGenerate resp) {
        putFrameIntoQueue(nodeId, resp, "");
    }

    private void putFrameIntoQueue(String nodeId, LLMGenerate resp, String finishReason) {
        String nodeType = nodeId.split(":")[0];
        if (false && (nodeType.equals(NodeTypeEnum.MESSAGE.getValue()) || nodeType.equals(NodeTypeEnum.END.getValue()) || "WorkflowEnd".equals(nodeId)) ) {
            ChatCallBackStreamResult result = new ChatCallBackStreamResult(nodeId, resp, finishReason);
            log.info("orderStreamResultQ nodeId: {}, finishReason: {}", nodeId, finishReason);
            this.orderStreamResultQ.offer(result);
        } else {
            log.info("putFrameIntoQueue nodeId: {}, finishReason: {}", nodeId, finishReason);
            this.streamQueue.offer(resp);
        }
    }
}