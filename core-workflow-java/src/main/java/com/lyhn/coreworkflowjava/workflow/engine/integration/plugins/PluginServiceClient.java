package com.lyhn.coreworkflowjava.workflow.engine.integration.plugins;

import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeState;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Node;
import com.lyhn.coreworkflowjava.workflow.engine.integration.plugins.aitools.AiToolsIntegration;
import com.lyhn.coreworkflowjava.workflow.engine.integration.plugins.tts.TtsIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
// 插件服务的统一入口，负责根据插件类型路由到具体的实现类
public class PluginServiceClient {
    // 通用的AI工具集成，可以调用各种外部工具
    @Autowired
    private AiToolsIntegration aiToolsIntegration;

    // 找到实现了 TtsIntegration 接口的 Bean
    // 专门用于语音合成的服务
    @Autowired
    private List<TtsIntegration> smartTTSIntegration;

    @Value("${tts.source:qwen}")
    private String ttsSource;

    public Map<String, Object> toolCall(NodeState nodeState, Map<String, Object> inputs) throws Exception {
        Node node = nodeState.node();
        Map<String, Object> output;
        // 通过判断 pluginId 来决定调用哪个插件
        if (Objects.equals(node.getData().getNodeParam().get("pluginId"), "tool@8b2262bef821000")) {
            // TTS 工具
            output = getTtsIntegration().call(nodeState, inputs);
        } else {
            output = aiToolsIntegration.call(nodeState, inputs);
        }

        return output;
    }

    public TtsIntegration getTtsIntegration() {
        for (TtsIntegration ttsIntegration : smartTTSIntegration) {
            if (Objects.equals(ttsIntegration.source(), ttsSource)) {
                return ttsIntegration;
            }
        }
        throw new RuntimeException("TTS 源不存在");
    }

}