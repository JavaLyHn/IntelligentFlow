package com.lyhn.coreworkflowjava.workflow.engine.node.impl.llm;

import com.lyhn.coreworkflowjava.workflow.engine.node.AbstractNodeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 将工作流中的业务数据（如用户输入、上下文历史）转换为标准的LLM请求，然后处理LLM返回的响应（包括流式和非流式），并将输出格式化为工作流可以继续处理的结果。
@Slf4j
@Component
public class LLMNodeExecutor extends AbstractNodeExecutor {

}