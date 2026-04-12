package com.lyhn.coreworkflowjava.workflow.engine.langgraph;

import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.node.NodeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class NodeExecutorFactory {

    private final Map<NodeTypeEnum, NodeExecutor> executorMap;

    public NodeExecutorFactory(List<NodeExecutor> executors) {
        this.executorMap = new HashMap<>();
        for (NodeExecutor executor : executors) {
            this.executorMap.put(executor.getNodeType(), executor);
        }
        log.info("[LangGraph] NodeExecutorFactory registered {} executors: {}",
                executorMap.size(), executorMap.keySet());
    }

    public NodeExecutor getExecutor(NodeTypeEnum nodeType) {
        NodeExecutor executor = executorMap.get(nodeType);
        if (executor == null) {
            throw new IllegalArgumentException(
                    "No NodeExecutor found for node type: " + nodeType);
        }
        return executor;
    }

    public boolean supports(NodeTypeEnum nodeType) {
        return executorMap.containsKey(nodeType);
    }

    public Map<NodeTypeEnum, NodeExecutor> getAllExecutors() {
        return new HashMap<>(executorMap);
    }
}
