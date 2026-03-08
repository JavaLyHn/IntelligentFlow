package com.lyhn.coreworkflowjava.workflow.engine.constants;

public enum MsgTypeEnum {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    THINKING("thinking");
    private String type;
    MsgTypeEnum(String type) {
        this.type = type;
    }
    public String getType() {
        return type;
    }
}
