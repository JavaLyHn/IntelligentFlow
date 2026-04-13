package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "workflow.agent.planning.enabled", havingValue = "true", matchIfMissing = true)
public class PlanningAgentAutoConfiguration {

    @Autowired(required = false)
    private PlanExecutor planExecutor;

    @Autowired(required = false)
    private SearchExecutor searchExecutor;

    @Autowired(required = false)
    private SynthesizeExecutor synthesizeExecutor;

    @Value("${workflow.agent.planning.storage-path:./plans}")
    private String storagePath;

    @Value("${workflow.agent.planning.max-iterations:10}")
    private int maxIterations;

    @Bean
    @ConditionalOnMissingBean
    public PlanPersistenceService planPersistenceService() {
        log.info("[PlanningAgentAutoConfiguration] Creating PlanPersistenceService with storagePath: {}", storagePath);
        return new PlanPersistenceService(Paths.get(storagePath));
    }

    @Bean
    @ConditionalOnMissingBean
    public PlanningAgent planningAgent(PlanPersistenceService persistenceService) {
        log.info("[PlanningAgentAutoConfiguration] Creating PlanningAgent");

        PlanExecutor effectivePlanExecutor = this.planExecutor;
        SearchExecutor effectiveSearchExecutor = this.searchExecutor;
        SynthesizeExecutor effectiveSynthesizeExecutor = this.synthesizeExecutor;

        if (effectivePlanExecutor == null) {
            effectivePlanExecutor = new FallbackPlanExecutor();
        }
        if (effectiveSearchExecutor == null) {
            effectiveSearchExecutor = new FallbackSearchExecutor();
        }
        if (effectiveSynthesizeExecutor == null) {
            effectiveSynthesizeExecutor = new FallbackSynthesizeExecutor();
        }

        return new PlanningAgent(
                effectivePlanExecutor,
                effectiveSearchExecutor,
                effectiveSynthesizeExecutor,
                persistenceService,
                maxIterations
        );
    }

    static class FallbackPlanExecutor implements PlanExecutor {
        @Override
        public ResearchPlan createPlan(String query) {
            return ResearchPlan.builder()
                    .query(query)
                    .steps(java.util.List.of(
                            ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                                    .description("Search overview").searchQuery(query + " overview").build(),
                            ResearchStep.builder().type(ResearchStep.StepType.SEARCH)
                                    .description("Search details").searchQuery(query + " details").build(),
                            ResearchStep.builder().type(ResearchStep.StepType.SYNTHESIZE)
                                    .description("Synthesize report").build()
                    ))
                    .build();
        }
    }

    static class FallbackSearchExecutor implements SearchExecutor {
        @Override
        public java.util.List<SearchResult> search(String query) {
            return java.util.List.of(SearchResult.builder()
                    .title("Result for: " + query)
                    .snippet("Default search result for: " + query)
                    .source("fallback")
                    .relevanceScore(0.5)
                    .build());
        }
    }

    static class FallbackSynthesizeExecutor implements SynthesizeExecutor {
        @Override
        public String synthesize(String query, String observations, String cycleHistory) {
            return "# Research Report: " + query + "\n\n## Findings\n\n" +
                    (observations != null ? observations : "No observations") +
                    "\n\n## Conclusion\n\nResearch completed.";
        }
    }
}
