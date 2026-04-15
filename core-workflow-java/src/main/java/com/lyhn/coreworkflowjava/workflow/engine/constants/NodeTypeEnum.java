package com.lyhn.coreworkflowjava.workflow.engine.constants;

public enum NodeTypeEnum {
    START("node-start"),
    END("node-end"),
    LLM("spark-llm"),
    KNOWLEDGE_BASE("knowledge-base"),
    KNOWLEDGE_PRO("knowledge-pro-base"),
    IF_ELSE("if-else"),
    CODE("ifly-code"),
    DECISION_MAKING("decision-making"),
    ITERATION("iteration"),
    ITERATION_START("iteration-node-start"),
    ITERATION_END("iteration-node-end"),
    PARAMETER_EXTRACTOR("extractor-parameter"),
    TEXT_JOINER("text-joiner"),
    FLOW("flow"),
    MESSAGE("message"),
    AGENT("agent"),
    PLUGIN("plugin"),
    QUESTION_ANSWER("question-answer"),
    DATABASE("database"),
    RPA("rpa"),

    CONDITION_SWITCH_NORMAL_ONE_OF("normal_one_of"),
    CONDITION_SWITCH_INTENT_CHAIN("intent_chain"),

    SKILL("skill"),
    RESEARCH("research"),
    MULTI_AGENT("multi-agent"),
    ;

    private final String value;

    NodeTypeEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Get NodeType by its string value
     *
     * @param value the string value of the node type
     * @return the corresponding NodeType enum, or null if not found
     */
    public static NodeTypeEnum fromValue(String value) {
        for (NodeTypeEnum type : NodeTypeEnum.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return value;
    }
}
