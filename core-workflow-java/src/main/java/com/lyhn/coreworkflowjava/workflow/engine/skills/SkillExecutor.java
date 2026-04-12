package com.lyhn.coreworkflowjava.workflow.engine.skills;

import com.lyhn.coreworkflowjava.workflow.engine.domain.WorkflowDSL;
import com.lyhn.coreworkflowjava.workflow.engine.langgraph.GraphBuilder;
import com.lyhn.coreworkflowjava.workflow.engine.langgraph.NodeExecutorFactory;
import com.lyhn.coreworkflowjava.workflow.engine.langgraph.WorkflowAgentState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class SkillExecutor {

    private final SkillRegistry skillRegistry;
    private final SkillMatcher skillMatcher;
    private final NodeExecutorFactory executorFactory;

    public SkillExecutor(SkillRegistry skillRegistry, SkillMatcher skillMatcher,
                         NodeExecutorFactory executorFactory) {
        this.skillRegistry = skillRegistry;
        this.skillMatcher = skillMatcher;
        this.executorFactory = executorFactory;
    }

    public SkillExecutionResult executeSkill(String skillName, Map<String, Object> inputs) {
        long startTime = System.currentTimeMillis();

        Optional<SkillDefinition> skillOpt = skillRegistry.getSkill(skillName);
        if (skillOpt.isEmpty()) {
            long elapsed = System.currentTimeMillis() - startTime;
            return SkillExecutionResult.failure(skillName,
                    "Skill not found: " + skillName, elapsed);
        }

        SkillDefinition skill = skillOpt.get();
        if (!skill.isEnabled()) {
            long elapsed = System.currentTimeMillis() - startTime;
            return SkillExecutionResult.failure(skillName,
                    "Skill is disabled: " + skillName, elapsed);
        }

        try {
            log.info("[SkillExecutor] Executing skill: {}", skillName);

            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("skillName", skill.getName());
            outputs.put("skillDescription", skill.getDescription());
            outputs.put("skillContent", skill.getContent());
            outputs.put("inputs", inputs);

            if (skill.getTemplate() != null) {
                outputs.put("template", skill.getTemplate());
                outputs.put("workflowHint", "Template loaded. Build workflow from template structure.");
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[SkillExecutor] Skill '{}' executed in {}ms", skillName, elapsed);
            return SkillExecutionResult.success(skillName, outputs, elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[SkillExecutor] Skill '{}' execution failed: {}", skillName, e.getMessage(), e);
            return SkillExecutionResult.failure(skillName, e.getMessage(), elapsed);
        }
    }

    public SkillExecutionResult executeByQuery(String query, Map<String, Object> inputs) {
        Optional<SkillDefinition> matched = skillMatcher.matchBest(query);
        if (matched.isEmpty()) {
            return SkillExecutionResult.failure("none",
                    "No matching skill found for query: " + query, 0);
        }
        return executeSkill(matched.get().getName(), inputs);
    }

    public Optional<WorkflowDSL> buildWorkflowFromTemplate(String skillName) {
        Optional<SkillDefinition> skillOpt = skillRegistry.getSkill(skillName);
        if (skillOpt.isEmpty() || skillOpt.get().getTemplate() == null) {
            return Optional.empty();
        }

        Map<String, Object> template = skillOpt.get().getTemplate();
        log.info("[SkillExecutor] Building workflow from template for skill: {}", skillName);

        return Optional.empty();
    }
}
