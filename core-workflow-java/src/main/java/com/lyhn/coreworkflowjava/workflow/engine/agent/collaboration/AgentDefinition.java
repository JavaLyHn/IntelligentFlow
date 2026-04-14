package com.lyhn.coreworkflowjava.workflow.engine.agent.collaboration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    private String agentId;

    private AgentRole role;

    private String name;

    private String systemPrompt;

    @Builder.Default
    private List<String> capabilities = new ArrayList<>();

    @Builder.Default
    private List<String> tools = new ArrayList<>();

    @Builder.Default
    private Map<String, Object> config = Map.of();

    public boolean hasCapability(String capability) {
        return capabilities != null && capabilities.contains(capability);
    }

    public boolean hasTool(String toolId) {
        return tools != null && tools.contains(toolId);
    }

    public static AgentDefinition supervisor(String name, String systemPrompt) {
        return AgentDefinition.builder()
                .agentId("supervisor-" + System.currentTimeMillis())
                .role(AgentRole.SUPERVISOR)
                .name(name)
                .systemPrompt(systemPrompt)
                .capabilities(List.of("task_decomposition", "quality_review", "delegation"))
                .build();
    }

    public static AgentDefinition searcher(String name, String systemPrompt) {
        return AgentDefinition.builder()
                .agentId("searcher-" + System.currentTimeMillis())
                .role(AgentRole.SEARCHER)
                .name(name)
                .systemPrompt(systemPrompt)
                .capabilities(List.of("web_search", "knowledge_search"))
                .build();
    }

    public static AgentDefinition analyzer(String name, String systemPrompt) {
        return AgentDefinition.builder()
                .agentId("analyzer-" + System.currentTimeMillis())
                .role(AgentRole.ANALYZER)
                .name(name)
                .systemPrompt(systemPrompt)
                .capabilities(List.of("data_analysis", "statistical_reasoning"))
                .build();
    }

    public static AgentDefinition writer(String name, String systemPrompt) {
        return AgentDefinition.builder()
                .agentId("writer-" + System.currentTimeMillis())
                .role(AgentRole.WRITER)
                .name(name)
                .systemPrompt(systemPrompt)
                .capabilities(List.of("report_writing", "summarization"))
                .build();
    }

    public static AgentDefinition judge(String name, String systemPrompt) {
        return AgentDefinition.builder()
                .agentId("judge-" + System.currentTimeMillis())
                .role(AgentRole.JUDGE)
                .name(name)
                .systemPrompt(systemPrompt)
                .capabilities(List.of("evaluation", "comparison", "synthesis"))
                .build();
    }
}
