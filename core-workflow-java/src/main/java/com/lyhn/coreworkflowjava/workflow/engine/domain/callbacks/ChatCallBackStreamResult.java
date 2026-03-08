package com.lyhn.coreworkflowjava.workflow.engine.domain.callbacks;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatCallBackStreamResult {
    /**
     * Unique identifier of the executed node.
     */
    private String nodeId;

    /**
     * Generated content from the node execution.
     */
    private LLMGenerate nodeAnswerContent;

    /**
     * Reason for node completion. 'stop' indicates normal completion, empty string otherwise.
     */
    private String finishReason = "";
}