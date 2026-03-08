package com.lyhn.coreworkflowjava.workflow.engine.domain.callbacks;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LLMGenerate {
    /**
     * Status code returned by the model or workflow engine.
     */
    private int code = 0;

    /**
     * Status message describing the result.
     */
    private String message = "Success";

    /**
     * Session identifier (sid) for tracking the request.
     */
    private String id;

    /**
     * The Unix timestamp (in seconds) of when the chat completion was created.
     */
    private long created;

    /**
     * Workflow execution step information.
     * This field is specific to workflow execution and not part of OpenAI's standard format.
     */
    @JsonProperty("workflow_step")
    private WorkflowStep workflowStep = new WorkflowStep();

    /**
     * List of response choices containing delta content.
     */
    private List<Choice> choices = new ArrayList<>();

    /**
     * Usage statistics for the completion request.
     */
    private GenerateUsage usage = null;

    /**
     * Interrupt event data if the workflow was interrupted.
     */
    @JsonProperty("event_data")
    private InterruptData eventData = null;

    public LLMGenerate() {
        this.created = System.currentTimeMillis() / 1000;
    }

    public LLMGenerate(int code, String message, String id, long created,
                       WorkflowStep workflowStep, List<Choice> choices,
                       GenerateUsage usage, InterruptData eventData) {
        this.code = code;
        this.message = message;
        this.id = id;
        this.created = created;
        this.workflowStep = workflowStep;
        this.choices = choices;
        this.usage = usage;
        this.eventData = eventData;
    }

    public static LLMGenerate common(String sid, int code, String message,
                                     GenerateUsage workflowUsage, NodeInfo nodeInfo,
                                     double progress, String content,
                                     String reasoningContent, String finishReason) {
        WorkflowStep workflowStep = new WorkflowStep(nodeInfo, 0, progress);

        Delta delta = new Delta("assistant", content, reasoningContent);
        Choice choice = new Choice(delta, 0, finishReason);

        List<Choice> choices = new ArrayList<>();
        choices.add(choice);

        LLMGenerate resp = new LLMGenerate();
        resp.setCode(code);
        resp.setMessage(message);
        resp.setId(sid);
        resp.setCreated(System.currentTimeMillis() / 1000);
        resp.setWorkflowStep(workflowStep);
        resp.setChoices(choices);
        if(workflowUsage != null) {
            resp.setUsage(workflowUsage);
        }
        return resp;
    }

    public static LLMGenerate interrupt(String sid, InterruptData eventData, int code,
                                        String message, NodeInfo nodeInfo, double progress,
                                        String finishReason) {
        WorkflowStep workflowStep = new WorkflowStep(nodeInfo, 0, progress);

        Delta delta = new Delta("assistant", "", "");
        Choice choice = new Choice(delta, 0, finishReason);

        List<Choice> choices = new ArrayList<>();
        choices.add(choice);

        LLMGenerate resp = new LLMGenerate();
        resp.setCode(code);
        resp.setMessage(message);
        resp.setId(sid);
        resp.setCreated(System.currentTimeMillis() / 1000);
        resp.setWorkflowStep(workflowStep);
        resp.setChoices(choices);
        resp.setEventData(eventData);

        return resp;
    }

    public static LLMGenerate ping(String sid, int code, String message,
                                   NodeInfo nodeInfo, double progress) {
        WorkflowStep workflowStep = new WorkflowStep(nodeInfo, 0, progress);

        Delta delta = new Delta("assistant", "", "");
        Choice choice = new Choice(delta, 0, "ping");

        List<Choice> choices = new ArrayList<>();
        choices.add(choice);

        LLMGenerate resp = new LLMGenerate();
        resp.setCode(code);
        resp.setMessage(message);
        resp.setId(sid);
        resp.setCreated(System.currentTimeMillis() / 1000);
        resp.setWorkflowStep(workflowStep);
        resp.setChoices(choices);

        return resp;
    }

    // WorkFlow 开始事件的回应
    public static LLMGenerate workflowStart(String sid) {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setId("flow_obj");
        nodeInfo.setAliasName("flow_start");
        nodeInfo.setFinishReason("stop");
        nodeInfo.setInputs(new HashMap<>());
        nodeInfo.setOutputs(new HashMap<>());
        nodeInfo.setExecutedTime(0);
        nodeInfo.setUsage(new GenerateUsage(0, 0, 0));

        return common(sid, 0, "Success", null, nodeInfo, 0, "", "", null);
    }

    public static LLMGenerate workflowEnd(String sid, GenerateUsage workflowUsage,
                                          int code, String message) {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setId("flow_obj");
        nodeInfo.setAliasName("flow_end");
        nodeInfo.setFinishReason("stop");
        nodeInfo.setInputs(new HashMap<>());
        nodeInfo.setOutputs(new HashMap<>());
        nodeInfo.setExecutedTime(0);
        nodeInfo.setUsage(new GenerateUsage(0, 0, 0));

        return common(sid, code, message, workflowUsage, nodeInfo, 1, "", "", "stop");
    }

    public static LLMGenerate workflowEndError(String sid, int code, String message) {
        GenerateUsage usage = new GenerateUsage(0, 0, 0);
        LLMGenerate llmGenerate = workflowEnd(sid, usage, code, message);
        if (llmGenerate.getWorkflowStep() != null) {
            llmGenerate.getWorkflowStep().setNode(null);
        }
        return llmGenerate;
    }

    public static LLMGenerate nodeStart(String sid, String nodeId, String aliasName,
                                        double progress, int code, String message) {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setId(nodeId);
        nodeInfo.setAliasName("Node:" + aliasName);
        nodeInfo.setFinishReason(null);
        nodeInfo.setInputs(new HashMap<>());
        nodeInfo.setOutputs(new HashMap<>());
        nodeInfo.setExecutedTime(0);

        return common(sid, code, message, null, nodeInfo, progress, "", "", null);
    }

    public static LLMGenerate nodeProcess(String sid, String nodeId, String aliasName,
                                          double nodeExecutedTime, Map<String, Object> nodeExt,
                                          double progress, String content, String reasoningContent,
                                          int code, String message) {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setId(nodeId);
        nodeInfo.setAliasName("Node:" + aliasName);
        nodeInfo.setFinishReason(null);
        nodeInfo.setInputs(new HashMap<>());
        nodeInfo.setOutputs(new HashMap<>());
        nodeInfo.setExecutedTime(nodeExecutedTime);
        nodeInfo.setExt(nodeExt);

        return common(sid, code, message, null, nodeInfo, progress, content, reasoningContent, null);
    }

    public static LLMGenerate nodeInterrupt(String sid, String eventId, Map<String, Object> value,
                                            String nodeId, String aliasName, double nodeExecutedTime,
                                            Map<String, Object> nodeExt, double progress,
                                            String finishReason, boolean needReply, int code,
                                            String message) {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setId(nodeId);
        nodeInfo.setAliasName("Node:" + aliasName);
        nodeInfo.setFinishReason(finishReason);
        nodeInfo.setInputs(new HashMap<>());
        nodeInfo.setOutputs(new HashMap<>());
        nodeInfo.setExecutedTime(nodeExecutedTime);
        nodeInfo.setExt(nodeExt);

        InterruptData eventData = new InterruptData();
        eventData.setEventId(eventId);
        eventData.setEventType("interrupt");
        eventData.setNeedReply(needReply);
        eventData.setValue(value);

        return interrupt(sid, eventData, code, message, nodeInfo, progress, finishReason);
    }

    // Getters and setters
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public WorkflowStep getWorkflowStep() {
        return workflowStep;
    }

    public void setWorkflowStep(WorkflowStep workflowStep) {
        this.workflowStep = workflowStep;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public GenerateUsage getUsage() {
        return usage;
    }

    public void setUsage(GenerateUsage usage) {
        this.usage = usage;
    }

    public InterruptData getEventData() {
        return eventData;
    }

    public void setEventData(InterruptData eventData) {
        this.eventData = eventData;
    }
}