package com.lyhn.coreworkflowjava.workflow.engine.agent.collaboration;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentRole {
    SUPERVISOR("supervisor", "任务拆解与分发"),
    SEARCHER("searcher", "信息搜索专家"),
    ANALYZER("analyzer", "数据分析专家"),
    WRITER("writer", "内容写作专家"),
    JUDGE("judge", "结果评判专家");

    private final String value;
    private final String description;

    public static AgentRole fromValue(String value) {
        if (value == null || value.isEmpty()) return SEARCHER;
        for (AgentRole role : values()) {
            if (role.value.equalsIgnoreCase(value.trim())) return role;
        }
        return SEARCHER;
    }
}
