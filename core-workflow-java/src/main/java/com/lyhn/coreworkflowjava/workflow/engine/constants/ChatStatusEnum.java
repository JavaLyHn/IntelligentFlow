package com.lyhn.coreworkflowjava.workflow.engine.constants;

import lombok.Getter;

@Getter
public enum ChatStatusEnum {
    PING("ping"),
    RUNNING("running"),
    STOP("stop"),
    ERROR("interrupt"),
    ;

    private String status;

    ChatStatusEnum(String status) {
        this.status = status;
    }
}
