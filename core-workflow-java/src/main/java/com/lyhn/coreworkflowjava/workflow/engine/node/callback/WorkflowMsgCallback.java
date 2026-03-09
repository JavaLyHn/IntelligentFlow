package com.lyhn.coreworkflowjava.workflow.engine.node.callback;

import com.lyhn.coreworkflowjava.workflow.engine.constants.EndNodeOutputModeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeRunResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.callbacks.ChatCallBackStreamResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.callbacks.ChatCallBacks;
import com.lyhn.coreworkflowjava.workflow.engine.domain.callbacks.LLMGenerate;
import com.lyhn.coreworkflowjava.workflow.engine.node.StreamCallback;
import com.lyhn.coreworkflowjava.workflow.engine.util.AsyncUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Slf4j
// 实现StreamCallback接口 当节点开始执行或者执行完毕以后 引擎会主动触发对应的回调方法 以确保客户端能实时收到工作流的执行状态
public class WorkflowMsgCallback implements StreamCallback {
    private final ChatCallBacks chatCallBacks;
    private final StreamCallback clientCallback;

    private volatile boolean tag = true;
    private volatile boolean scheduleTaskOver = false;

    // streamQueue是数据流队列，用于存放原始消息
    // OrderStreamResultQ是有序队列，用于存放需要有序返回的消息
    public WorkflowMsgCallback(String sid,
                               StreamCallback clientCallback,
                               EndNodeOutputModeEnum endNodeOutputMode,
                               Queue<LLMGenerate> streamQueue,
                               Queue<ChatCallBackStreamResult> needOrderStreamResultQ) {
        this.clientCallback = clientCallback;
        this.chatCallBacks = new ChatCallBacks(
                sid,
                streamQueue, // streamQueue is handled internally
                endNodeOutputMode,
                Set.of(),
                needOrderStreamResultQ
        );

        // 写一个任务，用于从streamQueue获取数据，发送给用户
        AsyncUtil.execute(() -> {
            while (tag) {
                try {
                    // 使用阻塞的方式等待数据
                    while(streamQueue.isEmpty() && tag) {
                        AsyncUtil.sleep(10); // 短暂休眠避免忙等待
                    }

                    LLMGenerate resp = streamQueue.poll();
                    if(resp != null) {
                        clientCallback.callback("stream", resp);
                    }
                }catch (Exception e) {
                    log.error("Error in stream callback", e);
                }
            }
            scheduleTaskOver = true;
        });
    }

    @Override
    public void callback(String eventType, Object data) {
        clientCallback.callback(eventType, data);
    }

    public void onWorkflowStart() {
        chatCallBacks.onSparkflowStart();
    }

    public void onWorkflowEnd(NodeRunResult message) {
        chatCallBacks.onSparkflowEnd(message);
    }

    public void onNodeStart(int code, String nodeId, String aliasName) {
        chatCallBacks.onNodeStart(code, nodeId, aliasName);
    }

    public void onNodeProcess(int code, String nodeId, String aliasName,
                              String message, String reasoningContent) {
        chatCallBacks.onNodeProcess(code, nodeId, aliasName, message, reasoningContent);
    }

    /**
     * Handle node interrupt event
     *
     * @param eventId      Unique identifier for the interrupt event
     * @param value        Interrupt event data
     * @param nodeId       Unique identifier of the interrupted node
     * @param aliasName    Human-readable name for the node
     * @param code         Status code for the interrupt operation
     * @param finishReason Reason for the interrupt
     * @param needReply    Whether a reply is needed for the interrupt
     */
    public void onNodeInterrupt(String eventId, Map<String, Object> value,
                                String nodeId, String aliasName, int code,
                                String finishReason, boolean needReply) {
        chatCallBacks.onNodeInterrupt(eventId, value, nodeId, aliasName, code, finishReason, needReply);
    }

    /**
     * Handle node end event
     *
     * @param nodeId    Unique identifier of the completed node
     * @param aliasName Human-readable name for the node
     * @param message   Node execution result, null if execution failed
     */
    public void onNodeEnd(String nodeId, String aliasName,
                          NodeRunResult message) {
        chatCallBacks.onNodeEnd(nodeId, aliasName, message, message.getError());
    }

    public void finished(){
        tag = false;
        while(!scheduleTaskOver) {
            AsyncUtil.sleep(10);
        }

        // 需要等待异步线程任务执行完毕，然后再执行下面的内容
        while (!chatCallBacks.getStreamQueue().isEmpty()) {
            var resp = chatCallBacks.getStreamQueue().poll();
            callback("stream", resp);
        }

        while (!chatCallBacks.getOrderStreamResultQ().isEmpty()) {
            var resp = chatCallBacks.getOrderStreamResultQ().poll();
            clientCallback.callback("stream", resp.getNodeAnswerContent());
        }

        clientCallback.finished();
    }

    /**
     * Handle end node executed event
     */
    public void onEndNodeExecuted(String nodeId, String aliasName, NodeRunResult message) {
        message.setNodeAnswerContent((String) message.getOutputs().getOrDefault("content", ""));
        message.setNodeAnswerReasoningContent((String) message.getOutputs().getOrDefault("reasoning_content", ""));
        message.setOutputs(message.getInputs());
        message.setInputs(Map.of());
        onNodeEnd(nodeId, aliasName, message);
    }

    /**
     * Handle start node executed event
     */
    public void onStartNodeExecuted(String nodeId, String aliasName, NodeRunResult message) {
        message.setOutputs(Map.of());
        this.onNodeEnd(nodeId, aliasName, message);
    }
}