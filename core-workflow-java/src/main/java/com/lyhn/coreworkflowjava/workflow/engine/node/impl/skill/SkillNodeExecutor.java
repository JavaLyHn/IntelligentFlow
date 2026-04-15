package com.lyhn.coreworkflowjava.workflow.engine.node.impl.skill;

import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeExecStatusEnum;
import com.lyhn.coreworkflowjava.workflow.engine.constants.NodeTypeEnum;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeRunResult;
import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeState;
import com.lyhn.coreworkflowjava.workflow.engine.node.AbstractNodeExecutor;
import com.lyhn.coreworkflowjava.workflow.engine.skills.SkillExecutor;
import com.lyhn.coreworkflowjava.workflow.engine.skills.SkillExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class SkillNodeExecutor extends AbstractNodeExecutor {

    private final SkillExecutor skillExecutor;

    public SkillNodeExecutor(SkillExecutor skillExecutor) {
        this.skillExecutor = skillExecutor;
    }

    @Override
    public NodeTypeEnum getNodeType() {
        return NodeTypeEnum.SKILL;
    }

    @Override
    protected NodeRunResult executeNode(NodeState nodeState, Map<String, Object> inputs) throws Exception {
        Map<String, Object> nodeParam = nodeState.node().getData().getNodeParam();
        String skillName = nodeParam != null ? (String) nodeParam.get("skillName") : null;
        String query = nodeParam != null ? (String) nodeParam.get("query") : null;

        if (skillName == null || skillName.isEmpty()) {
            if (query == null || query.isEmpty()) {
                throw new IllegalArgumentException("Either 'skillName' or 'query' must be provided for SKILL node");
            }
        }

        SkillExecutionResult skillResult;
        if (skillName != null && !skillName.isEmpty()) {
            log.info("[SkillNodeExecutor] Executing skill by name: {}", skillName);
            skillResult = skillExecutor.executeSkill(skillName, inputs);
        } else {
            log.info("[SkillNodeExecutor] Executing skill by query: {}", query);
            skillResult = skillExecutor.executeByQuery(query, inputs);
        }

        Map<String, Object> outputs = new HashMap<>();
        if (skillResult.isSuccess()) {
            outputs.put("skillOutput", skillResult.getOutputs());
            outputs.put("skillName", skillResult.getSkillName());
            outputs.put("elapsedMs", skillResult.getExecutionTimeMs());
        } else {
            outputs.put("skillError", skillResult.getErrorMessage());
            outputs.put("skillName", skillResult.getSkillName());
        }

        NodeRunResult result = new NodeRunResult();
        result.setInputs(inputs);
        result.setOutputs(outputs);
        result.setRawOutput(skillResult.isSuccess() ? skillResult.getOutputs().toString() : skillResult.getErrorMessage());
        result.setStatus(skillResult.isSuccess() ? NodeExecStatusEnum.SUCCESS : NodeExecStatusEnum.ERR_INTERUPT);
        return result;
    }
}
