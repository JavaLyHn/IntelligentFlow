package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import java.util.List;

public interface SearchExecutor {
    List<SearchResult> search(String query);
}
