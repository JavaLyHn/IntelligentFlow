package com.lyhn.coreworkflowjava.workflow.engine.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 提示词中的变量替换
public class VariableTemplateRender {
    // 匹配所有{{变量名}}格式的占位符
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    public static String render(String template, Map<String, Object> inputs) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String reference = matcher.group(1);
            Object value = inputs.get(reference);
            if(value == null) continue;
            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(value)));
        }
        // 添加剩余部分
        matcher.appendTail(result);
        return result.toString();
    }
}