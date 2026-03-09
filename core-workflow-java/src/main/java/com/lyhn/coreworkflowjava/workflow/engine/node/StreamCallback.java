package com.lyhn.coreworkflowjava.workflow.engine.node;

// 负责实时推送工作流的执行状态
public interface StreamCallback {
    void callback(String eventType,Object data);

    default void finished(){

    }
}