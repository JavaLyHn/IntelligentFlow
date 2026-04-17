package com.lyhn.coreworkflowjava.workflow.engine.agent.collaboration;

import com.lyhn.coreworkflowjava.workflow.engine.agent.research.SearchExecutor;
import com.lyhn.coreworkflowjava.workflow.engine.agent.research.SynthesizeExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(name = "workflow.agent.collaboration.enabled", havingValue = "true", matchIfMissing = true)
public class MultiAgentAutoConfiguration {

    @Autowired(required = false)
    private SearchExecutor searchExecutor;

    @Autowired(required = false)
    private SynthesizeExecutor synthesizeExecutor;

    @Bean
    @ConditionalOnMissingBean
    public MultiAgentOrchestrator multiAgentOrchestrator() {
        log.info("[MultiAgentAutoConfiguration] Creating MultiAgentOrchestrator");
        return new MultiAgentOrchestrator(searchExecutor, synthesizeExecutor);
    }
}
