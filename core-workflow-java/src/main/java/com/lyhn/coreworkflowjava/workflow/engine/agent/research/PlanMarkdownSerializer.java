package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlanMarkdownSerializer {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String toMarkdown(ResearchPlan plan, String planId) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Research Plan\n\n");

        sb.append("## Metadata\n\n");
        sb.append("| Field | Value |\n");
        sb.append("|-------|-------|\n");
        sb.append("| Plan ID | ").append(planId != null ? planId : "").append(" |\n");
        sb.append("| Query | ").append(escapeMarkdown(plan.getQuery())).append(" |\n");
        sb.append("| Status | ").append(plan.getStatus().name()).append(" |\n");
        sb.append("| Current Step | ").append(plan.getCurrentStepIndex()).append(" |\n");
        sb.append("| Total Steps | ").append(plan.getStepCount()).append(" |\n");
        sb.append("| Created At | ").append(LocalDateTime.now().format(FORMATTER)).append(" |\n");
        sb.append("\n");

        sb.append("## Steps\n\n");
        if (plan.getSteps() != null) {
            for (int i = 0; i < plan.getSteps().size(); i++) {
                ResearchStep step = plan.getSteps().get(i);
                sb.append("### Step ").append(i + 1).append(": ").append(escapeMarkdown(step.getDescription())).append("\n\n");
                sb.append("| Field | Value |\n");
                sb.append("|-------|-------|\n");
                sb.append("| Type | ").append(step.getType().name()).append(" |\n");
                sb.append("| Status | ").append(step.getStatus().name()).append(" |\n");
                if (step.getSearchQuery() != null && !step.getSearchQuery().isEmpty()) {
                    sb.append("| Search Query | ").append(escapeMarkdown(step.getSearchQuery())).append(" |\n");
                }
                if (step.getResult() != null && !step.getResult().isEmpty()) {
                    sb.append("| Result | ").append(escapeMarkdown(step.getResult())).append(" |\n");
                }
                sb.append("\n");
            }
        }

        sb.append("## Progress\n\n");
        sb.append("- Completed: ").append(plan.getCompletedStepCount()).append("/").append(plan.getStepCount()).append("\n");
        sb.append("- Status: ").append(plan.getStatus().name()).append("\n");

        return sb.toString();
    }

    public ResearchPlan fromMarkdown(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return null;
        }

        String query = extractTableValue(markdown, "Query");
        String statusStr = extractTableValue(markdown, "Status");
        String currentStepStr = extractTableValue(markdown, "Current Step");

        ResearchPlan.PlanStatus status = parsePlanStatus(statusStr);
        int currentStepIndex = parseIntSafe(currentStepStr, 0);

        List<ResearchStep> steps = parseSteps(markdown);

        return ResearchPlan.builder()
                .query(query)
                .steps(steps)
                .currentStepIndex(currentStepIndex)
                .status(status)
                .build();
    }

    public String extractPlanId(String markdown) {
        return extractTableValue(markdown, "Plan ID");
    }

    public String buildExecutionContext(ResearchPlan plan, int fromStepIndex) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Current Research Context\n\n");
        sb.append("## Research Query\n\n");
        sb.append(plan.getQuery()).append("\n\n");

        sb.append("## Plan Overview\n\n");
        sb.append("- Total Steps: ").append(plan.getStepCount()).append("\n");
        sb.append("- Current Step: ").append(plan.getCurrentStepIndex() + 1).append("\n");
        sb.append("- Status: ").append(plan.getStatus().name()).append("\n\n");

        sb.append("## Completed Steps\n\n");
        if (plan.getSteps() != null) {
            for (int i = 0; i < plan.getSteps().size() && i < fromStepIndex; i++) {
                ResearchStep step = plan.getSteps().get(i);
                sb.append("### Step ").append(i + 1).append(" (COMPLETED)\n\n");
                sb.append("- Description: ").append(step.getDescription()).append("\n");
                sb.append("- Type: ").append(step.getType().name()).append("\n");
                if (step.getResult() != null) {
                    sb.append("- Result Summary: ").append(truncate(step.getResult(), 200)).append("\n");
                }
                sb.append("\n");
            }
        }

        sb.append("## Current and Upcoming Steps\n\n");
        if (plan.getSteps() != null) {
            for (int i = Math.max(fromStepIndex, 0); i < plan.getSteps().size(); i++) {
                ResearchStep step = plan.getSteps().get(i);
                String marker = i == fromStepIndex ? "**[CURRENT]** " : "";
                sb.append("### ").append(marker).append("Step ").append(i + 1).append("\n\n");
                sb.append("- Description: ").append(step.getDescription()).append("\n");
                sb.append("- Type: ").append(step.getType().name()).append("\n");
                if (step.getSearchQuery() != null) {
                    sb.append("- Search Query: ").append(step.getSearchQuery()).append("\n");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private List<ResearchStep> parseSteps(String markdown) {
        List<ResearchStep> steps = new ArrayList<>();

        Pattern stepPattern = Pattern.compile("### Step \\d+: (.+?)(?=\n)");
        Matcher stepMatcher = stepPattern.matcher(markdown);

        List<Integer> stepStarts = new ArrayList<>();
        List<String> stepDescriptions = new ArrayList<>();

        while (stepMatcher.find()) {
            stepStarts.add(stepMatcher.start());
            stepDescriptions.add(unescapeMarkdown(stepMatcher.group(1).trim()));
        }

        for (int i = 0; i < stepDescriptions.size(); i++) {
            int start = stepStarts.get(i);
            int end = (i + 1 < stepStarts.size()) ? stepStarts.get(i + 1) : markdown.length();
            String stepSection = markdown.substring(start, end);

            String typeStr = extractTableValue(stepSection, "Type");
            String statusStr = extractTableValue(stepSection, "Status");
            String searchQuery = extractTableValue(stepSection, "Search Query");
            String result = extractTableValue(stepSection, "Result");

            ResearchStep step = ResearchStep.builder()
                    .type(parseStepType(typeStr))
                    .description(stepDescriptions.get(i))
                    .searchQuery(searchQuery != null && !searchQuery.isEmpty() ? searchQuery : null)
                    .status(parseStepStatus(statusStr))
                    .result(result != null && !result.isEmpty() ? result : null)
                    .build();

            steps.add(step);
        }

        return steps;
    }

    private String extractTableValue(String text, String key) {
        Pattern pattern = Pattern.compile("\\| " + Pattern.quote(key) + " \\| (.+?) \\|");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return unescapeMarkdown(matcher.group(1).trim());
        }
        return "";
    }

    private ResearchStep.StepType parseStepType(String typeStr) {
        if (typeStr == null || typeStr.isEmpty()) return ResearchStep.StepType.SEARCH;
        try {
            return ResearchStep.StepType.valueOf(typeStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return ResearchStep.StepType.SEARCH;
        }
    }

    private ResearchStep.StepStatus parseStepStatus(String statusStr) {
        if (statusStr == null || statusStr.isEmpty()) return ResearchStep.StepStatus.PENDING;
        try {
            return ResearchStep.StepStatus.valueOf(statusStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return ResearchStep.StepStatus.PENDING;
        }
    }

    private ResearchPlan.PlanStatus parsePlanStatus(String statusStr) {
        if (statusStr == null || statusStr.isEmpty()) return ResearchPlan.PlanStatus.PENDING;
        try {
            return ResearchPlan.PlanStatus.valueOf(statusStr.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return ResearchPlan.PlanStatus.PENDING;
        }
    }

    private int parseIntSafe(String value, int defaultValue) {
        if (value == null || value.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("|", "\\|").replace("\n", " ");
    }

    private String unescapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("\\|", "|");
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}
