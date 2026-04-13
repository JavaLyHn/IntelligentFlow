package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import com.lyhn.coreworkflowjava.workflow.engine.integration.model.OpenAiStyleLlmIntegration;
import com.lyhn.coreworkflowjava.workflow.engine.integration.model.bo.LlmCallback;
import com.lyhn.coreworkflowjava.workflow.engine.integration.model.bo.LlmReqBo;
import com.lyhn.coreworkflowjava.workflow.engine.integration.model.bo.LlmResVo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LlmSynthesizeExecutor implements SynthesizeExecutor {

    private static final String SYNTHESIZE_PROMPT = """
            You are a research synthesis assistant. Given a research query and collected observations, write a comprehensive research report.
            
            The report should:
            1. Start with an executive summary
            2. Cover all key findings from the research
            3. Include specific details and evidence
            4. Provide a conclusion with actionable insights
            5. List key sources referenced
            
            Research Query: %s
            
            Collected Observations:
            %s
            
            Research Process:
            %s
            
            Write the comprehensive research report:
            """;

    private final OpenAiStyleLlmIntegration llmIntegration;
    private final String model;
    private final String url;
    private final String apiKey;

    public LlmSynthesizeExecutor(OpenAiStyleLlmIntegration llmIntegration,
                                 String model, String url, String apiKey) {
        this.llmIntegration = llmIntegration;
        this.model = model;
        this.url = url;
        this.apiKey = apiKey;
    }

    @Override
    public String synthesize(String query, String observations, String cycleHistory) {
        log.info("[LlmSynthesizeExecutor] Synthesizing report for: {}", query);

        try {
            String prompt = String.format(SYNTHESIZE_PROMPT, query,
                    observations != null ? observations : "No observations collected",
                    cycleHistory != null ? cycleHistory : "No cycle history");

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
            String report = result.content();
            log.info("[LlmSynthesizeExecutor] Report generated: {} chars", report.length());
            return report;
        } catch (Exception e) {
            log.error("[LlmSynthesizeExecutor] Synthesis failed: {}", e.getMessage());
            return generateFallbackReport(query, observations);
        }
    }

    private String generateFallbackReport(String query, String observations) {
        StringBuilder report = new StringBuilder();
        report.append("# Research Report: ").append(query).append("\n\n");
        report.append("## Executive Summary\n\n");
        report.append("This report presents findings from research on: ").append(query).append(".\n\n");
        report.append("## Findings\n\n");

        if (observations != null && !observations.isEmpty()) {
            report.append(observations);
        } else {
            report.append("No detailed observations were collected during the research process.\n");
        }

        report.append("\n## Conclusion\n\n");
        report.append("Based on the available information, further research may be needed to draw definitive conclusions about ").append(query).append(".\n");

        return report.toString();
    }
}
