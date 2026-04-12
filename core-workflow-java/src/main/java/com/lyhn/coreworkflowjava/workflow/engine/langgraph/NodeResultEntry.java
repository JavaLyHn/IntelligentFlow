package com.lyhn.coreworkflowjava.workflow.engine.langgraph;

import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeExecStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeResultEntry implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private String nodeId;
    private String nodeType;
    private String aliasName;
    private NodeExecStatusEnum status;
    private java.util.Map<String, Object> outputs;
    private String errorMessage;
    private long executedTimeMs;
}
