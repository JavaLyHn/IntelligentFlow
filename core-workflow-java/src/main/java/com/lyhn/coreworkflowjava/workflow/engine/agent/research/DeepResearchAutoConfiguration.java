package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import com.lyhn.coreworkflowjava.workflow.engine.integration.model.OpenAiStyleLlmIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "workflow.agent.research.enabled", havingValue = "true", matchIfMissing = true)
public class DeepResearchAutoConfiguration {

    @Autowired(required = false)
    private OpenAiStyleLlmIntegration llmIntegration;

    @Value("${workflow.agent.research.model:deepseek-chat}")
    private String model;

    @Value("${workflow.agent.research.url:https://api.deepseek.com/v1/chat/completions}")
    private String url;

    @Value("${workflow.agent.research.api-key:}")
    private String apiKey;

    @Bean
    @ConditionalOnMissingBean
    public PlanExecutor planExecutor() {
        log.info("[DeepResearchAutoConfiguration] Creating PlanExecutor");
        if (llmIntegration != null && apiKey != null && !apiKey.isEmpty()) {
            return new LlmPlanExecutor(llmIntegration, model, url, apiKey);
        }
        return new DefaultPlanExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    public SearchExecutor searchExecutor() {
        log.info("[DeepResearchAutoConfiguration] Creating SearchExecutor");
        if (llmIntegration != null && apiKey != null && !apiKey.isEmpty()) {
            return new LlmSearchExecutor(llmIntegration, model, url, apiKey);
        }
        return new DefaultSearchExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    public SynthesizeExecutor synthesizeExecutor() {
        log.info("[DeepResearchAutoConfiguration] Creating SynthesizeExecutor");
        if (llmIntegration != null && apiKey != null && !apiKey.isEmpty()) {
            return new LlmSynthesizeExecutor(llmIntegration, model, url, apiKey);
        }
        return new DefaultSynthesizeExecutor();
    }

    @Bean
    @ConditionalOnMissingBean
    public DeepResearchAgent deepResearchAgent(PlanExecutor planExecutor,
                                               SearchExecutor searchExecutor,
                                               SynthesizeExecutor synthesizeExecutor) {
        log.info("[DeepResearchAutoConfiguration] Creating DeepResearchAgent");
        return new DeepResearchAgent(planExecutor, searchExecutor, synthesizeExecutor);
    }

    private static class DefaultPlanExecutor implements PlanExecutor {
        @Override
        public ResearchPlan createPlan(String query) {
            List<ResearchStep> steps = new ArrayList<>();
            steps.add(ResearchStep.builder()
                    .type(ResearchStep.StepType.SEARCH)
                    .description("Search for overview information")
                    .searchQuery(query + " overview")
                    .status(ResearchStep.StepStatus.PENDING)
                    .build());
            steps.add(ResearchStep.builder()
                    .type(ResearchStep.StepType.SEARCH)
                    .description("Search for detailed analysis")
                    .searchQuery(query + " analysis")
                    .status(ResearchStep.StepStatus.PENDING)
                    .build());
            steps.add(ResearchStep.builder()
                    .type(ResearchStep.StepType.SYNTHESIZE)
                    .description("Synthesize findings into report")
                    .status(ResearchStep.StepStatus.PENDING)
                    .build());

            return ResearchPlan.builder()
                    .query(query)
                    .steps(steps)
                    .currentStepIndex(0)
                    .status(ResearchPlan.PlanStatus.PENDING)
                    .build();
        }
    }

    private static class DefaultSearchExecutor implements SearchExecutor {
        @Override
        public List<SearchResult> search(String query) {
            return List.of(SearchResult.builder()
                    .title("Result for: " + query)
                    .snippet("Default search result for query: " + query)
                    .source("default")
                    .relevanceScore(0.5)
                    .build());
        }
    }

    private static class DefaultSynthesizeExecutor implements SynthesizeExecutor {
        @Override
        public String synthesize(String query, String observations, String cycleHistory) {
            return "# Research Report: " + query + "\n\n## Findings\n\n" +
                    (observations != null ? observations : "No observations collected") +
                    "\n\n## Conclusion\n\nBased on available information, further research may be needed.";
        }
    }
}
