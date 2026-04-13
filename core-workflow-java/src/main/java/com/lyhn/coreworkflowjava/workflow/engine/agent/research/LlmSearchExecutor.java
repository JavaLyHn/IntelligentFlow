package com.lyhn.coreworkflowjava.workflow.engine.agent.research;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lyhn.coreworkflowjava.workflow.engine.integration.model.OpenAiStyleLlmIntegration;
import com.lyhn.coreworkflowjava.workflow.engine.integration.model.bo.LlmCallback;
import com.lyhn.coreworkflowjava.workflow.engine.integration.model.bo.LlmReqBo;
import com.lyhn.coreworkflowjava.workflow.engine.integration.model.bo.LlmResVo;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class LlmSearchExecutor implements SearchExecutor {

    private static final String SEARCH_PROMPT = """
            You are a research search assistant. Given a search query, provide relevant factual information as if you performed a web search.
            
            Return a JSON array of search results. Each result should have:
            - title: the title of the result
            - snippet: a brief summary of the content (2-3 sentences)
            - source: where this information comes from
            - relevanceScore: a score from 0.0 to 1.0 indicating relevance
            
            Search Query: %s
            
            Return ONLY a valid JSON array of 3-5 results, no other text:
            """;

    private final OpenAiStyleLlmIntegration llmIntegration;
    private final String model;
    private final String url;
    private final String apiKey;

    public LlmSearchExecutor(OpenAiStyleLlmIntegration llmIntegration,
                             String model, String url, String apiKey) {
        this.llmIntegration = llmIntegration;
        this.model = model;
        this.url = url;
        this.apiKey = apiKey;
    }

    @Override
    public List<SearchResult> search(String query) {
        log.info("[LlmSearchExecutor] Searching for: {}", query);

        try {
            String prompt = String.format(SEARCH_PROMPT, query);
            String response = callLlm(prompt);

            if (response == null || response.isEmpty()) {
                return createFallbackResults(query);
            }

            return parseSearchResults(response);
        } catch (Exception e) {
            log.error("[LlmSearchExecutor] Search failed: {}", e.getMessage());
            return createFallbackResults(query);
        }
    }

    private List<SearchResult> parseSearchResults(String response) {
        List<SearchResult> results = new ArrayList<>();
        try {
            String jsonStr = extractJsonArray(response);
            JSONArray resultsArray = JSON.parseArray(jsonStr);

            for (int i = 0; i < resultsArray.size(); i++) {
                JSONObject resultJson = resultsArray.getJSONObject(i);
                double score = resultJson.containsKey("relevanceScore")
                        ? resultJson.getDoubleValue("relevanceScore") : 0.5;
                SearchResult result = SearchResult.builder()
                        .title(resultJson.getString("title"))
                        .snippet(resultJson.getString("snippet"))
                        .source(resultJson.getString("source"))
                        .relevanceScore(score)
                        .build();
                results.add(result);
            }
        } catch (Exception e) {
            log.warn("[LlmSearchExecutor] Failed to parse search results: {}", e.getMessage());
        }
        return results;
    }

    private List<SearchResult> createFallbackResults(String query) {
        List<SearchResult> results = new ArrayList<>();
        results.add(SearchResult.builder()
                .title("Search result for: " + query)
                .snippet("Information related to " + query + " was requested but detailed results are not available.")
                .source("fallback")
                .relevanceScore(0.5)
                .build());
        return results;
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
            log.error("[LlmSearchExecutor] LLM call failed: {}", e.getMessage());
            return null;
        }
    }
}
