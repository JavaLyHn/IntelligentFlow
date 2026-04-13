package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

public interface SynthesizeExecutor {
    String synthesize(String query, String observations, String cycleHistory);
}
