package com.lyhn.coreworkflowjava.workflow.engine.skills;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SkillsTest {

    private SkillRegistry skillRegistry;
    private SkillMatcher skillMatcher;
    private SkillExecutor skillExecutor;

    @BeforeEach
    void setUp() {
        SkillLoader loader = new SkillLoader();
        skillRegistry = new SkillRegistry(loader);
        skillRegistry.init();

        skillMatcher = new SkillMatcher(skillRegistry);
        skillExecutor = new SkillExecutor(skillRegistry, skillMatcher, null);
    }

    @Test
    @DisplayName("SkillLoader - 加载所有 Skills")
    void testSkillLoaderLoadAll() {
        SkillLoader loader = new SkillLoader();
        List<SkillDefinition> skills = loader.loadAllSkills();

        assertFalse(skills.isEmpty(), "Should load at least one skill");
        System.out.println("Loaded skills: " + skills.stream().map(SkillDefinition::getName).toList());
    }

    @Test
    @DisplayName("SkillRegistry - 初始化后包含所有 Skills")
    void testSkillRegistryInit() {
        assertTrue(skillRegistry.size() >= 3, "Should have at least 3 skills registered");

        assertTrue(skillRegistry.contains("code-review"), "Should contain code-review");
        assertTrue(skillRegistry.contains("data-analysis"), "Should contain data-analysis");
        assertTrue(skillRegistry.contains("podcast-writing"), "Should contain podcast-writing");
    }

    @Test
    @DisplayName("SkillRegistry - 获取指定 Skill")
    void testSkillRegistryGetSkill() {
        Optional<SkillDefinition> skill = skillRegistry.getSkill("code-review");

        assertTrue(skill.isPresent(), "code-review should be present");
        assertEquals("code-review", skill.get().getName());
        assertNotNull(skill.get().getDescription());
        assertFalse(skill.get().getDescription().isEmpty());
        assertNotNull(skill.get().getContent());
        assertFalse(skill.get().getContent().isEmpty());
    }

    @Test
    @DisplayName("SkillRegistry - 获取不存在的 Skill 返回空")
    void testSkillRegistryGetNonExistent() {
        Optional<SkillDefinition> skill = skillRegistry.getSkill("non-existent-skill");
        assertTrue(skill.isEmpty(), "Non-existent skill should return empty");
    }

    @Test
    @DisplayName("SkillRegistry - 获取所有启用的 Skills")
    void testSkillRegistryGetEnabledSkills() {
        List<SkillDefinition> enabled = skillRegistry.getEnabledSkills();
        assertFalse(enabled.isEmpty(), "Should have enabled skills");
        for (SkillDefinition skill : enabled) {
            assertTrue(skill.isEnabled(), "All returned skills should be enabled");
        }
    }

    @Test
    @DisplayName("SkillDefinition - 关键词匹配")
    void testSkillDefinitionKeywordMatch() {
        SkillDefinition skill = SkillDefinition.builder()
                .name("test-skill")
                .keywords(List.of("PDF", "文档解析"))
                .triggers(List.of("解析PDF"))
                .enabled(true)
                .priority(1)
                .build();

        assertTrue(skill.matches("帮我解析PDF文件"), "Should match trigger");
        assertTrue(skill.matches("PDF处理"), "Should match keyword");
        assertTrue(skill.matches("test-skill"), "Should match name");
        assertFalse(skill.matches("今天天气怎么样"), "Should not match unrelated query");
    }

    @Test
    @DisplayName("SkillDefinition - 评分机制")
    void testSkillDefinitionMatchScore() {
        SkillDefinition skill = SkillDefinition.builder()
                .name("pdf-processor")
                .keywords(List.of("PDF", "文档"))
                .triggers(List.of("解析PDF", "提取PDF"))
                .enabled(true)
                .priority(3)
                .build();

        double triggerScore = skill.matchScore("帮我解析PDF文件");
        double keywordScore = skill.matchScore("PDF格式转换");
        double nameScore = skill.matchScore("pdf-processor");
        double noMatchScore = skill.matchScore("今天天气");

        assertTrue(triggerScore > keywordScore, "Trigger match should score higher than keyword");
        assertTrue(keywordScore > noMatchScore, "Keyword match should score higher than no match");
        assertTrue(nameScore > 0, "Name match should have positive score");
        assertEquals(0.0, noMatchScore, "No match should have zero score");
    }

    @Test
    @DisplayName("SkillMatcher - 代码审查场景匹配")
    void testSkillMatcherCodeReview() {
        Optional<SkillDefinition> matched = skillMatcher.matchBest("请帮我审查这段代码的质量");

        assertTrue(matched.isPresent(), "Should match a skill for code review query");
        assertEquals("code-review", matched.get().getName(),
                "Should match code-review skill");
    }

    @Test
    @DisplayName("SkillMatcher - PDF 解析场景匹配")
    void testSkillMatcherPdfProcessor() {
        Optional<SkillDefinition> matched = skillMatcher.matchBest("帮我解析PDF文件中的内容");

        assertTrue(matched.isPresent(), "Should match a skill for PDF query");
        assertEquals("pdf-processor", matched.get().getName(),
                "Should match pdf-processor skill");
    }

    @Test
    @DisplayName("SkillMatcher - 播客写作场景匹配")
    void testSkillMatcherPodcastWriting() {
        Optional<SkillDefinition> matched = skillMatcher.matchBest("帮我写一个播客脚本并生成语音");

        assertTrue(matched.isPresent(), "Should match a skill for podcast query");
        assertEquals("podcast-writing", matched.get().getName(),
                "Should match podcast-writing skill");
    }

    @Test
    @DisplayName("SkillMatcher - 数据分析场景匹配")
    void testSkillMatcherDataAnalysis() {
        Optional<SkillDefinition> matched = skillMatcher.matchBest("分析这份数据并生成报表");

        assertTrue(matched.isPresent(), "Should match a skill for data analysis query");
        assertEquals("data-analysis", matched.get().getName(),
                "Should match data-analysis skill");
    }

    @Test
    @DisplayName("SkillMatcher - 无匹配场景")
    void testSkillMatcherNoMatch() {
        Optional<SkillDefinition> matched = skillMatcher.matchBest("今天天气怎么样");

        assertTrue(matched.isEmpty(), "Should not match any skill for weather query");
    }

    @Test
    @DisplayName("SkillMatcher - 多匹配排序（高分优先）")
    void testSkillMatcherMultipleMatches() {
        List<SkillDefinition> matches = skillMatcher.matchAll("帮我审查代码并生成PDF报告");

        assertFalse(matches.isEmpty(), "Should have matches");
        if (matches.size() > 1) {
            double firstScore = matches.get(0).matchScore("帮我审查代码并生成PDF报告");
            double secondScore = matches.get(1).matchScore("帮我审查代码并生成PDF报告");
            assertTrue(firstScore >= secondScore,
                    "First match should have higher or equal score");
        }
    }

    @Test
    @DisplayName("SkillMatcher - buildSkillPrompt 生成技能提示词")
    void testSkillMatcherBuildPrompt() {
        String prompt = skillMatcher.buildSkillPrompt("请帮我审查这段代码");

        assertFalse(prompt.isEmpty(), "Prompt should not be empty");
        assertTrue(prompt.contains("当前激活技能"), "Prompt should contain skill header");
        assertTrue(prompt.contains("代码审查"), "Prompt should contain skill content");
    }

    @Test
    @DisplayName("SkillMatcher - 无匹配时返回空提示词")
    void testSkillMatcherBuildPromptNoMatch() {
        String prompt = skillMatcher.buildSkillPrompt("今天天气怎么样");

        assertTrue(prompt.isEmpty(), "Prompt should be empty when no match");
    }

    @Test
    @DisplayName("SkillExecutor - 按名称执行 Skill")
    void testSkillExecutorExecuteByName() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("code", "public void test() { int x = 1; }");
        inputs.put("language", "java");

        SkillExecutionResult result = skillExecutor.executeSkill("code-review", inputs);

        assertTrue(result.isSuccess(), "Execution should succeed");
        assertEquals("code-review", result.getSkillName());
        assertNotNull(result.getOutputs());
        assertEquals("code-review", result.getOutputs().get("skillName"));
        assertTrue(result.getExecutionTimeMs() >= 0);
    }

    @Test
    @DisplayName("SkillExecutor - 执行不存在的 Skill")
    void testSkillExecutorExecuteNonExistent() {
        SkillExecutionResult result = skillExecutor.executeSkill("non-existent", new HashMap<>());

        assertFalse(result.isSuccess(), "Should fail for non-existent skill");
        assertTrue(result.getErrorMessage().contains("Skill not found"));
    }

    @Test
    @DisplayName("SkillExecutor - 按查询自动匹配执行")
    void testSkillExecutorExecuteByQuery() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("file_url", "https://example.com/doc.pdf");

        SkillExecutionResult result = skillExecutor.executeByQuery("帮我解析这个PDF文件", inputs);

        assertTrue(result.isSuccess(), "Execution should succeed");
        assertEquals("pdf-processor", result.getSkillName());
        assertNotNull(result.getOutputs().get("template"));
    }

    @Test
    @DisplayName("SkillExecutor - 无匹配查询返回失败")
    void testSkillExecutorExecuteByQueryNoMatch() {
        SkillExecutionResult result = skillExecutor.executeByQuery("今天天气怎么样", new HashMap<>());

        assertFalse(result.isSuccess(), "Should fail when no skill matches");
        assertTrue(result.getErrorMessage().contains("No matching skill"));
    }

    @Test
    @DisplayName("SkillRegistry - 动态注册和注销 Skill")
    void testSkillRegistryDynamicRegisterUnregister() {
        int initialSize = skillRegistry.size();

        SkillDefinition customSkill = SkillDefinition.builder()
                .name("custom-skill")
                .description("自定义测试技能")
                .keywords(List.of("自定义", "custom"))
                .triggers(List.of("自定义操作"))
                .content("自定义技能内容")
                .enabled(true)
                .priority(0)
                .build();

        skillRegistry.register(customSkill);
        assertEquals(initialSize + 1, skillRegistry.size(), "Size should increase by 1");
        assertTrue(skillRegistry.contains("custom-skill"));

        skillRegistry.unregister("custom-skill");
        assertEquals(initialSize, skillRegistry.size(), "Size should decrease by 1");
        assertFalse(skillRegistry.contains("custom-skill"));
    }

    @Test
    @DisplayName("SkillRegistry - reload 重新加载")
    void testSkillRegistryReload() {
        int sizeBefore = skillRegistry.size();
        skillRegistry.reload();
        int sizeAfter = skillRegistry.size();

        assertEquals(sizeBefore, sizeAfter, "Size should be same after reload");
    }

    @Test
    @DisplayName("SkillExecutionResult - 静态工厂方法")
    void testSkillExecutionResultFactory() {
        Map<String, Object> outputs = Map.of("key", "value");

        SkillExecutionResult success = SkillExecutionResult.success("test", outputs, 100L);
        assertTrue(success.isSuccess());
        assertEquals("test", success.getSkillName());
        assertEquals(100L, success.getExecutionTimeMs());

        SkillExecutionResult failure = SkillExecutionResult.failure("test", "error msg", 50L);
        assertFalse(failure.isSuccess());
        assertEquals("error msg", failure.getErrorMessage());
    }

    @Test
    @DisplayName("SkillDefinition - template 加载验证")
    void testSkillDefinitionTemplateLoaded() {
        Optional<SkillDefinition> codeReview = skillRegistry.getSkill("code-review");

        assertTrue(codeReview.isPresent());
        assertNotNull(codeReview.get().getTemplate(), "Template should be loaded from template.json");
        assertNotNull(codeReview.get().getTemplatePath(), "Template path should be set");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) codeReview.get().getTemplate().get("nodes");
        assertNotNull(nodes, "Template should have nodes");
        assertFalse(nodes.isEmpty(), "Template nodes should not be empty");
    }

    @Test
    @DisplayName("PDF Skill - 完整验证")
    void testPdfSkillComplete() {
        Optional<SkillDefinition> pdfSkill = skillRegistry.getSkill("pdf-processor");

        assertTrue(pdfSkill.isPresent(), "pdf-processor should be registered");
        assertEquals("pdf-processor", pdfSkill.get().getName());
        assertTrue(pdfSkill.get().getDescription().contains("PDF"));
        assertNotNull(pdfSkill.get().getTemplate());

        String prompt = skillMatcher.buildSkillPrompt("解析PDF文件");
        assertTrue(prompt.contains("PDF"), "Prompt should contain PDF content");
        assertTrue(prompt.contains("工作流模板"), "Prompt should contain template section");

        SkillExecutionResult result = skillExecutor.executeSkill("pdf-processor",
                Map.of("file_url", "test.pdf"));
        assertTrue(result.isSuccess());
        assertNotNull(result.getOutputs().get("template"));
    }
}
