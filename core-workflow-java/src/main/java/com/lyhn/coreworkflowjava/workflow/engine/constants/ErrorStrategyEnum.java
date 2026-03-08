package com.lyhn.coreworkflowjava.workflow.engine.constants;

import lombok.Getter;

@Getter
public enum ErrorStrategyEnum {
    INTERUPT(0, "中断"), // 节点失败后，整个工作流停止，向上抛异常。适合关键节点，比如付款失败就不能继续发货
    ERR_CODE(1, "错误码"), // 节点失败后，用配置好的默认值作为输出，继续执行后续节点。适合非关键节点，比如日志记录失败不影响主流程
    ERR_CONDITION(2, "错误条件"), // 节点失败后，不走正常的 nextNodes，改走 failNodes 里的异常处理分支。适合需要特殊处理的场景，比如调用外部 API 失败就走降级逻辑
    ;

    private int code;
    private String msg;

    ErrorStrategyEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static ErrorStrategyEnum fromCode(int code) {
        for (ErrorStrategyEnum value : ErrorStrategyEnum.values()) {
            if (value.code == code) {
                return value;
            }
        }
        return null;
    }
}
