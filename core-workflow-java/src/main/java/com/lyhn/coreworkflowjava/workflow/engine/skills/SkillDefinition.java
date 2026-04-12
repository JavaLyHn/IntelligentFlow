package com.lyhn.coreworkflowjava.workflow.engine.skills;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDefinition implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    private String description;

    private List<String> keywords;

    private List<String> triggers;

    private String content;

    private String templatePath;

    private Map<String, Object> template;

    private List<String> examples;

    private int priority;

    private boolean enabled;

    public boolean matches(String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        String lowerQuery = query.toLowerCase();

        if (triggers != null) {
            for (String trigger : triggers) {
                if (lowerQuery.contains(trigger.toLowerCase())) {
                    return true;
                }
                if (matchesAllTokens(trigger, lowerQuery)) {
                    return true;
                }
            }
        }

        if (keywords != null) {
            for (String keyword : keywords) {
                if (lowerQuery.contains(keyword.toLowerCase())) {
                    return true;
                }
                if (matchesAllTokens(keyword, lowerQuery)) {
                    return true;
                }
            }
        }

        if (name != null && lowerQuery.contains(name.toLowerCase())) {
            return true;
        }

        return false;
    }

    private boolean matchesAllTokens(String pattern, String query) {
        String[] tokens = pattern.toLowerCase().split("[\\s\\-_.]+");
        if (tokens.length <= 1) {
            return containsAllChars(pattern, query);
        }
        for (String token : tokens) {
            if (!token.isEmpty() && !query.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsAllChars(String pattern, String query) {
        String normalizedPattern = pattern.toLowerCase().replaceAll("[\\s\\-_.]", "");
        String normalizedQuery = query.toLowerCase();
        for (char c : normalizedPattern.toCharArray()) {
            if (normalizedQuery.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    public double matchScore(String query) {
        if (query == null || query.isEmpty()) {
            return 0.0;
        }
        String lowerQuery = query.toLowerCase();
        double score = 0.0;

        if (triggers != null) {
            for (String trigger : triggers) {
                if (lowerQuery.contains(trigger.toLowerCase())) {
                    score += 3.0;
                } else if (matchesAllTokens(trigger, lowerQuery)) {
                    score += 2.0;
                }
            }
        }

        if (keywords != null) {
            for (String keyword : keywords) {
                if (lowerQuery.contains(keyword.toLowerCase())) {
                    score += 1.0;
                } else if (matchesAllTokens(keyword, lowerQuery)) {
                    score += 0.5;
                }
            }
        }

        if (name != null && lowerQuery.contains(name.toLowerCase())) {
            score += 2.0;
        }

        return score * (1 + priority * 0.1);
    }
}
