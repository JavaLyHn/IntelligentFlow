package com.lyhn.coreworkflowjava.workflow.engine.integration.plugins.tts;

import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeState;

import java.util.Map;

public interface TtsIntegration {
    // 返回插件标识符
    String source();

    // 执行TTS合成
    Map<String, Object> call(NodeState nodeState, Map<String, Object> inputs) throws Exception;
}
