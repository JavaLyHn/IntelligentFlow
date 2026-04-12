package com.lyhn.coreworkflowjava.workflow.engine.skills;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SkillRegistry {

    private final Map<String, SkillDefinition> skillMap = new ConcurrentHashMap<>();
    private final SkillLoader skillLoader;

    public SkillRegistry(SkillLoader skillLoader) {
        this.skillLoader = skillLoader;
    }

    @PostConstruct
    public void init() {
        List<SkillDefinition> skills = skillLoader.loadAllSkills();
        for (SkillDefinition skill : skills) {
            register(skill);
        }
        log.info("[SkillRegistry] Initialized with {} skills: {}", skillMap.size(), skillMap.keySet());
    }

    public void register(SkillDefinition skill) {
        if (skill == null || skill.getName() == null) {
            log.warn("[SkillRegistry] Cannot register skill with null name");
            return;
        }
        skillMap.put(skill.getName(), skill);
        log.debug("[SkillRegistry] Registered skill: {}", skill.getName());
    }

    public void unregister(String skillName) {
        skillMap.remove(skillName);
        log.debug("[SkillRegistry] Unregistered skill: {}", skillName);
    }

    public Optional<SkillDefinition> getSkill(String skillName) {
        return Optional.ofNullable(skillMap.get(skillName));
    }

    public List<SkillDefinition> getAllSkills() {
        return new ArrayList<>(skillMap.values());
    }

    public List<SkillDefinition> getEnabledSkills() {
        return skillMap.values().stream()
                .filter(SkillDefinition::isEnabled)
                .toList();
    }

    public int size() {
        return skillMap.size();
    }

    public boolean contains(String skillName) {
        return skillMap.containsKey(skillName);
    }

    public void reload() {
        skillMap.clear();
        List<SkillDefinition> skills = skillLoader.loadAllSkills();
        for (SkillDefinition skill : skills) {
            register(skill);
        }
        log.info("[SkillRegistry] Reloaded {} skills", skillMap.size());
    }
}
