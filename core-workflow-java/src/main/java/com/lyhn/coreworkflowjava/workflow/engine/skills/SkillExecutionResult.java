package com.lyhn.coreworkflowjava.workflow.engine.skills;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillExecutionResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String skillName;

    private boolean success;

    private Map<String, Object> outputs;

    private String errorMessage;

    private long executionTimeMs;

    public static SkillExecutionResult success(String skillName, Map<String, Object> outputs, long executionTimeMs) {
        return new SkillExecutionResult(skillName, true, outputs, null, executionTimeMs);
    }

    public static SkillExecutionResult failure(String skillName, String errorMessage, long executionTimeMs) {
        return new SkillExecutionResult(skillName, false, null, errorMessage, executionTimeMs);
    }
}
