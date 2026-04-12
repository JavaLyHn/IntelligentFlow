package com.lyhn.coreworkflowjava.workflow.engine.skills;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SkillLoader {

    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", Pattern.DOTALL
    );

    private static final String SKILL_RESOURCE_PATTERN = "classpath*:skills/*/SKILL.md";
    private static final String TEMPLATE_RESOURCE_PATTERN = "classpath*:skills/*/resources/template.json";

    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    public List<SkillDefinition> loadAllSkills() {
        List<SkillDefinition> skills = new ArrayList<>();

        try {
            Resource[] skillResources = resourceResolver.getResources(SKILL_RESOURCE_PATTERN);
            log.info("[SkillLoader] Found {} SKILL.md resources", skillResources.length);

            Map<String, Map<String, Object>> templates = loadAllTemplates();

            for (Resource resource : skillResources) {
                try {
                    SkillDefinition skill = parseSkillMd(resource, templates);
                    if (skill != null) {
                        skills.add(skill);
                        log.info("[SkillLoader] Loaded skill: {} (triggers: {})",
                                skill.getName(), skill.getTriggers());
                    }
                } catch (Exception e) {
                    log.error("[SkillLoader] Failed to parse skill from {}: {}",
                            resource.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("[SkillLoader] Failed to scan skill resources: {}", e.getMessage());
        }

        return skills;
    }

    private Map<String, Map<String, Object>> loadAllTemplates() {
        Map<String, Map<String, Object>> templates = new HashMap<>();

        try {
            Resource[] templateResources = resourceResolver.getResources(TEMPLATE_RESOURCE_PATTERN);
            for (Resource resource : templateResources) {
                try {
                    String json = readResourceContent(resource);
                    Map<String, Object> template = JSON.parseObject(json, Map.class);
                    String skillName = extractSkillNameFromPath(resource);
                    if (skillName != null) {
                        templates.put(skillName, template);
                    }
                } catch (Exception e) {
                    log.warn("[SkillLoader] Failed to parse template from {}: {}",
                            resource.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("[SkillLoader] Failed to scan template resources: {}", e.getMessage());
        }

        return templates;
    }

    private SkillDefinition parseSkillMd(Resource resource, Map<String, Map<String, Object>> templates) throws IOException {
        String content = readResourceContent(resource);
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        String skillDirName = extractSkillNameFromPath(resource);
        String name = skillDirName;
        String description = "";
        List<String> keywords = new ArrayList<>();
        List<String> triggers = new ArrayList<>();
        String body = content;
        int priority = 0;

        Matcher matcher = FRONT_MATTER_PATTERN.matcher(content.trim());
        if (matcher.find()) {
            String frontMatter = matcher.group(1);
            body = matcher.group(2);

            JSONObject fm = parseFrontMatter(frontMatter);
            if (fm.containsKey("name")) {
                name = fm.getString("name");
            }
            if (fm.containsKey("description")) {
                description = fm.getString("description");
            }
            if (fm.containsKey("keywords")) {
                keywords = parseStringList(fm.get("keywords"));
            }
            if (fm.containsKey("triggers")) {
                triggers = parseStringList(fm.get("triggers"));
            }
            if (fm.containsKey("priority")) {
                priority = fm.getIntValue("priority");
            }
        }

        SkillDefinition.SkillDefinitionBuilder builder = SkillDefinition.builder()
                .name(name)
                .description(description)
                .keywords(keywords)
                .triggers(triggers)
                .content(body)
                .priority(priority)
                .enabled(true);

        if (skillDirName != null && templates.containsKey(skillDirName)) {
            builder.template(templates.get(skillDirName));
            builder.templatePath("skills/" + skillDirName + "/resources/template.json");
        }

        return builder.build();
    }

    private JSONObject parseFrontMatter(String frontMatter) {
        JSONObject result = new JSONObject();
        String[] lines = frontMatter.split("\\n");
        String currentKey = null;
        List<String> currentList = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("- ") && currentKey != null && currentList != null) {
                currentList.add(trimmed.substring(2).trim());
                continue;
            }

            int colonIndex = trimmed.indexOf(':');
            if (colonIndex > 0) {
                if (currentKey != null && currentList != null && !currentList.isEmpty()) {
                    result.put(currentKey, currentList);
                }

                String key = trimmed.substring(0, colonIndex).trim();
                String value = trimmed.substring(colonIndex + 1).trim();

                if (value.isEmpty()) {
                    currentKey = key;
                    currentList = new ArrayList<>();
                } else {
                    result.put(key, value);
                    currentKey = null;
                    currentList = null;
                }
            }
        }

        if (currentKey != null && currentList != null && !currentList.isEmpty()) {
            result.put(currentKey, currentList);
        }

        return result;
    }

    private List<String> parseStringList(Object value) {
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                result.add(item.toString());
            }
            return result;
        }
        if (value instanceof String) {
            return Arrays.asList(((String) value).split(","));
        }
        return new ArrayList<>();
    }

    private String extractSkillNameFromPath(Resource resource) {
        try {
            String uri = resource.getURI().toString();
            int skillsIdx = uri.lastIndexOf("skills/");
            if (skillsIdx < 0) return null;
            String afterSkills = uri.substring(skillsIdx + "skills/".length());
            int slashIdx = afterSkills.indexOf('/');
            if (slashIdx < 0) return afterSkills;
            return afterSkills.substring(0, slashIdx);
        } catch (IOException e) {
            return null;
        }
    }

    private String readResourceContent(Resource resource) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }
}
