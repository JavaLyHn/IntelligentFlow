package com.lyhn.coreworkflowjava.workflow.engine.skills;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class SkillMatcher {

    private final SkillRegistry skillRegistry;

    public SkillMatcher(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    public Optional<SkillDefinition> matchBest(String query) {
        List<SkillDefinition> matches = matchAll(query);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matches.get(0));
    }

    public List<SkillDefinition> matchAll(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        return skillRegistry.getEnabledSkills().stream()
                .filter(skill -> skill.matches(query))
                .sorted(Comparator.comparingDouble((SkillDefinition s) -> s.matchScore(query)).reversed())
                .toList();
    }

    public String buildSkillPrompt(String query) {
        Optional<SkillDefinition> matched = matchBest(query);
        if (matched.isEmpty()) {
            return "";
        }

        SkillDefinition skill = matched.get();
        StringBuilder prompt = new StringBuilder();
        prompt.append("\n\n## 当前激活技能: ").append(skill.getName()).append("\n\n");
        prompt.append(skill.getContent());

        if (skill.getTemplate() != null && !skill.getTemplate().isEmpty()) {
            prompt.append("\n\n### 工作流模板\n");
            prompt.append("请按照以下模板结构执行任务:\n");
            prompt.append(formatTemplate(skill.getTemplate()));
        }

        log.info("[SkillMatcher] Matched skill '{}' for query (score: {})",
                skill.getName(), skill.matchScore(query));

        return prompt.toString();
    }

    private String formatTemplate(java.util.Map<String, Object> template) {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Object> entry : template.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ");
            if (entry.getValue() instanceof java.util.Map) {
                sb.append("\n");
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> nested = (java.util.Map<String, Object>) entry.getValue();
                for (java.util.Map.Entry<String, Object> ne : nested.entrySet()) {
                    sb.append("  - ").append(ne.getKey()).append(": ").append(ne.getValue()).append("\n");
                }
            } else if (entry.getValue() instanceof java.util.List) {
                sb.append("\n");
                @SuppressWarnings("unchecked")
                java.util.List<Object> list = (java.util.List<Object>) entry.getValue();
                for (Object item : list) {
                    sb.append("  - ").append(item).append("\n");
                }
            } else {
                sb.append(entry.getValue()).append("\n");
            }
        }
        return sb.toString();
    }
}
