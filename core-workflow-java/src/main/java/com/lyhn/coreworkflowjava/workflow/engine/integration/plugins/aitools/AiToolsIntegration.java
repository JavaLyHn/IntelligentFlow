package com.lyhn.coreworkflowjava.workflow.engine.integration.plugins.aitools;

import com.lyhn.coreworkflowjava.workflow.engine.domain.NodeState;
import com.lyhn.coreworkflowjava.workflow.engine.domain.chain.Node;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
// 连接 Java 版工作流引擎和外部插件服务之间的桥梁
// 它是一个 HTTP 客户端的封装。当 PluginServiceClient 确定一个插件调用的不是内置插件（如讯飞 TTS）时，它就会将任务委托给 AiToolsIntegration
public class AiToolsIntegration {
    @Value("${workflow.services.aitools-url}")
    private String aitoolsUrl;

    public Map<String, Object> call(NodeState nodeState, Map<String, Object> inputs) throws Exception {
        Node node = nodeState.node();
        log.info("Executing plugin node: {}", node.getId());

        Map<String, Object> nodeParam = node.getData().getNodeParam();
        if (nodeParam == null) {
            throw new IllegalArgumentException("Node parameters are null");
        }

        // 1. 参数解析 (pluginId, operationId, etc.) ...
        String pluginId = getString(nodeParam, "pluginId");
        String operationId = getString(nodeParam, "operationId");
        String appId = getString(nodeParam, "appId");

        if (pluginId == null || pluginId.isEmpty()) {
            throw new IllegalArgumentException("Plugin ID is required");
        }

        if (operationId == null || operationId.isEmpty()) {
            throw new IllegalArgumentException("Operation ID is required");
        }

        if (appId == null || appId.isEmpty()) {
            throw new IllegalArgumentException("App ID is required");
        }

        String version = getString(nodeParam, "version");
        if (version == null || version.isEmpty()) {
            version = "V1.0";
        }

        List<String> businessInput = getList(nodeParam, "businessInput");
        if (businessInput == null) {
            businessInput = new ArrayList<>();
        }

        // Initialize Link client for tool communication
        // 2. 初始化 Link 客户端，这是关键的第一阶段：服务发现
        String pluginBaseUrl = aitoolsUrl;

        Link link = new Link(
                appId,
                Arrays.asList(pluginId),
                pluginBaseUrl + "/api/v1/tools/versions",// 发现服务的 URL
                pluginBaseUrl + "/api/v1/tools/http_run",// 执行服务的 URL
                version
        );

        Map<String, Object> actionInputs = inputs != null ? new HashMap<>(inputs) : new HashMap<>();
        Map<String, Object> outputs = new HashMap<>();

        // Find and execute the matching tool operation
        // 3. 遍历已发现的工具，执行匹配的操作，这是第二阶段：执行
        boolean toolFound = false;
        if (link.getTools() != null) {
            for (Tool tool : link.getTools()) {
                if (tool != null && operationId.equals(tool.getOperationId())) {
                    toolFound = true;
                    Map<String, Object> businessInputMap = getBusinessInput(actionInputs, businessInput);

                    try {
                        // Execute the tool operation
                        // 直接调用 Tool 对象上的 run 方法
                        Map<String, Object> res = tool.run(actionInputs, businessInputMap);
                        outputs = res != null ? res : new HashMap<>();
                        break;
                    } catch (Exception e) {
                        log.error("Error executing plugin: {}", e.getMessage(), e);
                        throw new RuntimeException("Plugin execution failed: " + e.getMessage(), e);
                    }
                }
            }
        }

        if (!toolFound) {
            // Handle case where plugin operation is not found
            log.error("Plugin not found: {}", operationId);
            throw new UnsupportedOperationException("Plugin not found: " + operationId);
        }

        log.info("Plugin node completed: {}", node.getId());
        return outputs;
    }

    /**
     * Extract business input parameters from action input using recursive traversal.
     *
     * @param actionInput   The input data structure to search through
     * @param businessInput List of keys to extract
     * @return Map containing extracted business input parameters
     */
    private Map<String, Object> getBusinessInput(Map<String, Object> actionInput, List<String> businessInput) {
        if (businessInput == null || businessInput.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> businessInputMap = new HashMap<>();

        // 遍历所有业务输入键，递归提取
        // Iterate through each business input key to extract
        for (String inputKey : businessInput) {
            if (inputKey == null || inputKey.isEmpty()) {
                continue;
            }

            // Use iterative approach with queue for breadth-first search
            Queue<Object> iterQueue = new LinkedList<>();
            if (actionInput != null) {
                iterQueue.offer(actionInput);
            }

            while (!iterQueue.isEmpty()) {
                Object iterOne = iterQueue.poll();

                if (iterOne instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> iterMap = (Map<String, Object>) iterOne;

                    // Check if current key matches the target business input key
                    if (iterMap.containsKey(inputKey)) {
                        businessInputMap.put(inputKey, iterMap.get(inputKey));
                        break;
                    }

                    // Add nested structures to queue for further processing
                    for (Object value : iterMap.values()) {
                        if (value instanceof Map || value instanceof List) {
                            iterQueue.offer(value);
                        }
                    }
                } else if (iterOne instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> iterList = (List<Object>) iterOne;

                    // Add all nested structures from list to queue
                    for (Object content : iterList) {
                        if (content instanceof Map || content instanceof List) {
                            iterQueue.offer(content);
                        }
                    }
                }
            }
        }

        return businessInputMap;
    }

    public String getString(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private List<String> getList(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        Object value = map.get(key);
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) value;
            return list;
        }
        return null;
    }
}
