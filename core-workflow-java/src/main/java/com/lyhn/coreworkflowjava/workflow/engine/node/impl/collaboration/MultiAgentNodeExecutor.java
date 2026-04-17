package com.lyhn.coreworkflowjava.workflow.engine.node.impl.collaboration;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.lyhn.coreworkflowjava.workflow.engine.agent.collaboration.*;
import com.lyhn.coreworkflowjava.workflow.engine.agent.research.SearchExecutor;
import com.lyhn.coreworkflowjava.workflow.engine.agent.research.SynthesizeExecutor;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeExecStatusEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeRunResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeState;
import com.lyhn.coreworkflowjava.workflow.engine.node.AbstractNodeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class MultiAgentNodeExecutor extends AbstractNodeExecutor {

    @Autowired(required = false)
    private SearchExecutor searchExecutor;

    @Autowired(required = false)
    private SynthesizeExecutor synthesizeExecutor;

    @Override
    public NodeTypeEnum getNodeType() {
        return NodeTypeEnum.MULTI_AGENT;
    }

    @Override
    protected NodeRunResult executeNode(NodeState nodeState, Map<String, Object> inputs) throws Exception {
        Map<String, Object> nodeParam = nodeState.node().getData().getNodeParam();

        String mode = nodeParam != null ? (String) nodeParam.getOrDefault("collaborationMode", "supervisor") : "supervisor";
        String query = extractQuery(nodeParam, inputs);
        int maxIterations = nodeParam != null ? parseIntSafe(String.valueOf(nodeParam.getOrDefault("maxIterations", "10")), 10) : 10;

        List<AgentDefinition> agents = parseAgents(nodeParam);
        if (agents.isEmpty()) {
            agents = createDefaultAgents(mode);
        }

        log.info("[MultiAgentNodeExecutor] Starting: mode={}, agents={}, query={}, maxIterations={}",
                mode, agents.size(), query, maxIterations);

        MultiAgentOrchestrator orchestrator = new MultiAgentOrchestrator(searchExecutor, synthesizeExecutor);
        MultiAgentResult result = orchestrator.orchestrate(mode, agents, query, maxIterations);

        Map<String, Object> outputs = new HashMap<>();
        if (result.isSuccess()) {
            outputs.put("finalOutput", result.getFinalOutput());
            outputs.put("mode", result.getMode());
            outputs.put("totalIterations", result.getTotalIterations());
            outputs.put("elapsedMs", result.getElapsedMs());
            outputs.put("agentResults", result.getAgentResults());
        } else {
            outputs.put("error", result.getErrorMessage());
        }

        NodeRunResult runResult = new NodeRunResult();
        runResult.setInputs(inputs);
        runResult.setOutputs(outputs);
        runResult.setRawOutput(result.isSuccess() ? result.getFinalOutput() : result.getErrorMessage());
        runResult.setStatus(result.isSuccess() ? NodeExecStatusEnum.SUCCESS : NodeExecStatusEnum.ERR_INTERUPT);
        return runResult;
    }

    private String extractQuery(Map<String, Object> nodeParam, Map<String, Object> inputs) {
        if (nodeParam != null && nodeParam.get("query") != null) {
            return String.valueOf(nodeParam.get("query"));
        }
        if (inputs != null && inputs.get("query") != null) {
            return String.valueOf(inputs.get("query"));
        }
        if (inputs != null && inputs.get("text") != null) {
            return String.valueOf(inputs.get("text"));
        }
        return "Default task";
    }

    @SuppressWarnings("unchecked")
    private List<AgentDefinition> parseAgents(Map<String, Object> nodeParam) {
        if (nodeParam == null || nodeParam.get("agents") == null) {
            return Collections.emptyList();
        }

        try {
            Object agentsObj = nodeParam.get("agents");
            if (agentsObj instanceof String) {
                return JSON.parseObject((String) agentsObj, new TypeReference<List<AgentDefinition>>() {});
            } else {
                String json = JSON.toJSONString(agentsObj);
                return JSON.parseObject(json, new TypeReference<List<AgentDefinition>>() {});
            }
        } catch (Exception e) {
            log.warn("[MultiAgentNodeExecutor] Failed to parse agents config: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<AgentDefinition> createDefaultAgents(String mode) {
        return switch (mode.toLowerCase()) {
            case "supervisor" -> List.of(
                    AgentDefinition.supervisor("研究主管", "你是一个研究主管，负责拆解任务并分发给专家"),
                    AgentDefinition.searcher("搜索专家", "你是一个搜索专家，擅长信息检索"),
                    AgentDefinition.analyzer("分析专家", "你是一个分析专家，擅长数据分析"),
                    AgentDefinition.writer("写作专家", "你是一个写作专家，擅长撰写研究报告")
            );
            case "pipeline" -> List.of(
                    AgentDefinition.searcher("搜索员", "负责信息搜索"),
                    AgentDefinition.analyzer("分析员", "负责数据分析"),
                    AgentDefinition.writer("写作员", "负责报告撰写")
            );
            case "swarm" -> List.of(
                    AgentDefinition.searcher("搜索员A", "负责方向A的搜索"),
                    AgentDefinition.searcher("搜索员B", "负责方向B的搜索"),
                    AgentDefinition.judge("评判员", "负责综合评判所有搜索结果")
            );
            default -> List.of(
                    AgentDefinition.supervisor("主管", "负责协调"),
                    AgentDefinition.searcher("搜索员", "负责搜索")
            );
        };
    }

    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
