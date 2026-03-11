package com.lyhn.coreworkflowjava.workflow.engine.node.impl;

import com.lyhn.coreworkflowjava.workflow.engine.constants.EndNodeOutputModeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeExecStatusEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeRunResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeState;
import com.lyhn.coreworkflowjava.workflow.engine.node.AbstractNodeExecutor;
import com.lyhn.coreworkflowjava.workflow.engine.util.VariableTemplateRender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class EndNodeExecutor extends AbstractNodeExecutor {
    @Override
    public NodeTypeEnum getNodeType() {
        return NodeTypeEnum.END;
    }

    // 支持多种输出模式：既可以直接返回变量池中的原始结果，也可以通过模板方式，对最终输出进行格式化
    @Override
    protected NodeRunResult executeNode(NodeState node, Map<String, Object> inputs) {
        Map<String, Object> nodeParam = node.node().getData().getNodeParam();

        // 返回结果有两种定义
        // 1. 自定义格式进行返回
        // 2. 直接返回
        Integer outputMode = getOutputMode(nodeParam);

        String finalOutput;
        String finalReason = "";

        if (Objects.equals(outputMode, EndNodeOutputModeEnum.VARIABLE_MODE.getMode())) {
            String template = getTemplate(nodeParam);
            if (!StringUtils.isEmpty(template)) {
                finalOutput = VariableTemplateRender.render(template, inputs);
                log.info("End node: formatted output using template (length={})", finalOutput.length());
            } else {
                finalOutput = toStr(inputs);
            }

            String reasoningTemplate = getReasonTemplate(nodeParam);
            if (!StringUtils.isEmpty(reasoningTemplate)) {
                finalReason = VariableTemplateRender.render(reasoningTemplate, inputs);
            }
        } else {
            finalOutput = toStr(inputs);
        }

        Map<String, Object> outputs = new HashMap<>();
        // 输出结果
        outputs.put("content", finalOutput);
        // 输出的思考过程
        outputs.put("reasoning_content", finalReason);

        NodeRunResult result = new NodeRunResult();
        result.setInputs(inputs);
        result.setOutputs(outputs);
        result.setStatus(NodeExecStatusEnum.SUCCESS);
        return result;
    }

    private Integer getOutputMode(Map<String, Object> nodeParam) {
        Object outputModeObj = nodeParam.get("outputMode");
        if (outputModeObj instanceof Integer) {
            return (Integer) outputModeObj;
        } else if (outputModeObj instanceof Number) {
            return ((Number) outputModeObj).intValue();
        }
        return 1;
    }

    private String getTemplate(Map<String, Object> nodeParam) {
        Object templateObj = nodeParam.get("template");
        return templateObj != null ? String.valueOf(templateObj) : "";
    }

    private String getReasonTemplate(Map<String, Object> nodeParam) {
        Object templateObj = nodeParam.get("reasoningTemplate");
        return templateObj != null ? String.valueOf(templateObj) : "";
    }

    private String toStr(Map<String, Object> inputs) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
}