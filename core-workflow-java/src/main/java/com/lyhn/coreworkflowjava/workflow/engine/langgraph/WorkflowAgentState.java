package com.lyhn.coreworkflowjava.workflow.engine.langgraph;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class WorkflowAgentState extends AgentState {

    public static final String VARIABLE_POOL = "variablePool";
    public static final String NODE_RESULTS = "nodeResults";
    public static final String CALLBACK = "callback";
    public static final String INPUTS = "inputs";
    public static final String ERROR = "error";
    public static final String FLOW_ID = "flowId";
    public static final String CHAT_ID = "chatId";

    public static final Map<String, Channel<?>> SCHEMA = createSchema();

    private static Map<String, Channel<?>> createSchema() {
        Map<String, Channel<?>> schema = new LinkedHashMap<>();
        schema.put(VARIABLE_POOL, Channels.base((java.util.function.Supplier<Map<String, Map<String, Object>>>) HashMap::new));
        schema.put(NODE_RESULTS, Channels.appender(ArrayList::new));
        schema.put(CALLBACK, Channels.base((java.util.function.Supplier<String>) () -> ""));
        schema.put(INPUTS, Channels.base((java.util.function.Supplier<Map<String, Object>>) HashMap::new));
        schema.put(ERROR, Channels.base((java.util.function.Supplier<String>) () -> ""));
        schema.put(FLOW_ID, Channels.base((java.util.function.Supplier<String>) () -> ""));
        schema.put(CHAT_ID, Channels.base((java.util.function.Supplier<String>) () -> ""));
        return schema;
    }

    public WorkflowAgentState(Map<String, Object> initData) {
        super(initData);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> getVariablePoolData() {
        return value(VARIABLE_POOL)
                .map(m -> (Map<String, Map<String, Object>>) m)
                .orElse(new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    public java.util.List<NodeResultEntry> getNodeResults() {
        return value(NODE_RESULTS)
                .map(l -> (java.util.List<NodeResultEntry>) l)
                .orElse(new ArrayList<>());
    }

    public String getFlowId() {
        return value(FLOW_ID)
                .map(Object::toString)
                .orElse("");
    }

    public String getChatId() {
        return value(CHAT_ID)
                .map(Object::toString)
                .orElse("");
    }
}
