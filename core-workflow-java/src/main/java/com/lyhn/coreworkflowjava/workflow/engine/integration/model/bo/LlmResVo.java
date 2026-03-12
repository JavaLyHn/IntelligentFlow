package com.lyhn.coreworkflowjava.workflow.engine.integration.model.bo;
import org.springframework.ai.chat.metadata.Usage;

// 封装从模型API接收到的响应数据
public record LlmResVo(Usage usage, String content, String thinkContent) {
}
