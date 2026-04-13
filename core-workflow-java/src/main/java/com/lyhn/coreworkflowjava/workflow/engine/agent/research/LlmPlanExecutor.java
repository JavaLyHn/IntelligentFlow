package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lyhn.coreworkflowjava.workflow.engine.integration.model.OpenAiStyleLlmIntegration;
import com.lyhn.coreworkflowjava.workflow.engine.integration.model.bo.LlmCallback;
import com.lyhn.coreworkflowjava.workflow.engine.integration.model.bo.LlmReqBo;
import com.lyhn.coreworkflowjava.workflow.engine.integration.model.bo.LlmResVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.*;

@Slf4j
public class LlmPlanExecutor implements PlanExecutor {

    private static final String PLAN_PROMPT = """
            You are a research planning assistant. Given a research query, break it down into a structured plan with specific search steps.
            
            For each step, provide:
            - type: one of "SEARCH" or "SYNTHESIZE"
            - description: what this step aims to accomplish
            - searchQuery: the specific search query to use (for SEARCH steps)
            
            Return a JSON array of steps. The last step should always be a SYNTHESIZE type.
            
            Research Query: %s
            
            Return ONLY a valid JSON array, no other text:
            """;

    private final OpenAiStyleLlmIntegration llmIntegration;
    private final String model;
    private final String url;
    private final String apiKey;

    public LlmPlanExecutor(OpenAiStyleLlmIntegration llmIntegration,
                           String model, String url, String apiKey) {
        this.llmIntegration = llmIntegration;
        this.model = model;
        this.url = url;
        this.apiKey = apiKey;
    }

    @Override
    public ResearchPlan createPlan(String query) {
        log.info("[LlmPlanExecutor] Creating research plan for: {}", query);

        List<ResearchStep> steps = parsePlanFromLlm(query);

        if (steps.isEmpty()) {
            steps = createDefaultPlan(query);
        }

        return ResearchPlan.builder()
                .query(query)
                .steps(steps)
                .currentStepIndex(0)
                .status(ResearchPlan.PlanStatus.PENDING)
                .build();
    }

    private List<ResearchStep> parsePlanFromLlm(String query) {
        try {
            String prompt = String.format(PLAN_PROMPT, query);
            String response = callLlm(prompt);

            if (response == null || response.isEmpty()) {
                return new ArrayList<>();
            }

            String jsonStr = extractJsonArray(response);
            JSONArray stepsArray = JSON.parseArray(jsonStr);

            List<ResearchStep> steps = new ArrayList<>();
            for (int i = 0; i < stepsArray.size(); i++) {
                JSONObject stepJson = stepsArray.getJSONObject(i);
                ResearchStep step = ResearchStep.builder()
                        .type(mapStepType(stepJson.getString("type")))
                        .description(stepJson.getString("description"))
                        .searchQuery(stepJson.getString("searchQuery"))
                        .status(ResearchStep.StepStatus.PENDING)
                        .build();
                steps.add(step);
            }

            return steps;
        } catch (Exception e) {
            log.warn("[LlmPlanExecutor] Failed to parse LLM plan, using default: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<ResearchStep> createDefaultPlan(String query) {
        List<ResearchStep> steps = new ArrayList<>();
        steps.add(ResearchStep.builder()
                .type(ResearchStep.StepType.SEARCH)
                .description("Search for overview information")
                .searchQuery(query + " overview introduction")
                .status(ResearchStep.StepStatus.PENDING)
                .build());
        steps.add(ResearchStep.builder()
                .type(ResearchStep.StepType.SEARCH)
                .description("Search for detailed analysis")
                .searchQuery(query + " analysis details")
                .status(ResearchStep.StepStatus.PENDING)
                .build());
        steps.add(ResearchStep.builder()
                .type(ResearchStep.StepType.SEARCH)
                .description("Search for latest developments")
                .searchQuery(query + " latest news updates")
                .status(ResearchStep.StepStatus.PENDING)
                .build());
        steps.add(ResearchStep.builder()
                .type(ResearchStep.StepType.SYNTHESIZE)
                .description("Synthesize all findings into a comprehensive report")
                .status(ResearchStep.StepStatus.PENDING)
                .build());
        return steps;
    }

    private ResearchStep.StepType mapStepType(String type) {
        if (type == null) return ResearchStep.StepType.SEARCH;
        return switch (type.toUpperCase().trim()) {
            case "SYNTHESIZE" -> ResearchStep.StepType.SYNTHESIZE;
            default -> ResearchStep.StepType.SEARCH;
        };
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String callLlm(String prompt) {
        try {
            LlmReqBo req = new LlmReqBo();
            req.setUserMsg(prompt);
            req.setModel(model);
            req.setUrl(url);
            req.setApiKey(apiKey);

            StringBuilder responseBuilder = new StringBuilder();
            LlmCallback callback = chatResponse -> {
                if (chatResponse.getResults() != null && !chatResponse.getResults().isEmpty()) {
                    String text = chatResponse.getResult().getOutput().getText();
                    if (text != null && !text.isEmpty()) {
                        responseBuilder.append(text);
                    }
                }
            };

            LlmResVo result = llmIntegration.call(req, callback);
            return result.content();
        } catch (Exception e) {
            log.error("[LlmPlanExecutor] LLM call failed: {}", e.getMessage());
            return null;
        }
    }
}
